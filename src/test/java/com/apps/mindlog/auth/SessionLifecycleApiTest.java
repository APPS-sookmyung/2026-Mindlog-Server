package com.apps.mindlog.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.apps.mindlog.global.security.AccessTokenCodec;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Import(SessionLifecycleApiTest.Database.class)
@TestPropertySource(properties = "mindlog.security.master-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
class SessionLifecycleApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AccessTokenCodec tokens;
    @Autowired Clock clock;
    private long userId;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM refresh_sessions");
        jdbc.update("DELETE FROM device_tokens");
        jdbc.update("DELETE FROM users");
        jdbc.update("INSERT INTO emotion_characters(id,code,name,display_order,active,created_at,updated_at) VALUES(1,'anger','테스트',1,true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) ON CONFLICT(id) DO NOTHING");
        jdbc.update("""
                INSERT INTO users(email,password_hash,nickname,representative_character_id,account_status,onboarding_completed,
                    after_reminder_enabled,weekly_report_start_day,created_at,updated_at)
                VALUES('session@example.com',NULL,'테스트',1,'active',true,true,'sun',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """);
        userId = jdbc.queryForObject("SELECT id FROM users WHERE email='session@example.com'", Long.class);
    }

    @Test
    void refreshRotatesTokenAtomicallyAndOldTokenCannotBeReused() throws Exception {
        String originalRefresh = issueSession(null, Instant.now(clock).plus(30, ChronoUnit.DAYS));
        var response = mvc.perform(post("/api/auth/sessions/refresh").header("X-API-Version", "1")
                        .contentType("application/json").content("{\"refreshToken\":\"" + originalRefresh + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty()).andExpect(jsonPath("$.expiresIn").value(3600))
                .andReturn();
        String rotated = com.jayway.jsonpath.JsonPath.read(response.getResponse().getContentAsString(), "$.refreshToken");
        assertThat(rotated).isNotEqualTo(originalRefresh);
        assertThat(jdbc.queryForObject("SELECT revoked_at IS NOT NULL FROM refresh_sessions WHERE refresh_token_hash=?",
                Boolean.class, tokens.refreshTokenHash(originalRefresh))).isTrue();
        mvc.perform(post("/api/auth/sessions/refresh").header("X-API-Version", "1").contentType("application/json")
                        .content("{\"refreshToken\":\"" + originalRefresh + "\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.type").value("https://api.mindlog.com/problems/invalid-refresh-token"));
        mvc.perform(post("/api/auth/sessions/refresh").header("X-API-Version", "1").contentType("application/json")
                        .content("{\"refreshToken\":\"" + rotated + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void refreshDistinguishesExpiredAndUnknownTokens() throws Exception {
        String expired = issueSession(null, Instant.now(clock).minus(1, ChronoUnit.SECONDS));
        mvc.perform(refresh(expired)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("https://api.mindlog.com/problems/expired-refresh-token"));
        mvc.perform(refresh("unregistered-refresh-token")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("https://api.mindlog.com/problems/invalid-refresh-token"));
        mvc.perform(post("/api/auth/sessions/refresh").header("X-API-Version", "1").contentType("application/json")
                        .content("{\"refreshToken\":\" \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void logoutRevokesOnlyCurrentSessionAndCleansItsUnusedDeviceToken() throws Exception {
        String logoutRefresh = issueDeviceSession("logout-device");
        String token = accessForRefresh(logoutRefresh);
        String otherRefresh = issueSession(null, Instant.now(clock).plus(30, ChronoUnit.DAYS));
        mvc.perform(delete("/api/auth/sessions/current").header("X-API-Version", "1").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT revoked_at IS NOT NULL FROM refresh_sessions WHERE refresh_token_hash=?",
                Boolean.class, tokens.refreshTokenHash(otherRefresh))).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM device_tokens WHERE token='logout-device'", Integer.class)).isZero();
        mvc.perform(refresh(logoutRefresh)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("https://api.mindlog.com/problems/invalid-refresh-token"));
    }

    @Test
    void logoutKeepsDeviceTokenStillUsedByAnotherActiveSession() throws Exception {
        Long deviceId = jdbc.queryForObject("INSERT INTO device_tokens(user_id,token,platform,created_at,updated_at) VALUES(?,?,'web',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) RETURNING id", Long.class, userId, "shared-device");
        String logoutRefresh = issueSession(deviceId, Instant.now(clock).plus(30, ChronoUnit.DAYS));
        String retainedRefresh = issueSession(deviceId, Instant.now(clock).plus(30, ChronoUnit.DAYS));
        String access = accessForRefresh(logoutRefresh);
        mvc.perform(delete("/api/auth/sessions/current").header("X-API-Version", "1").header("Authorization", "Bearer " + access))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM device_tokens WHERE token='shared-device'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT revoked_at IS NULL FROM refresh_sessions WHERE refresh_token_hash=?", Boolean.class,
                tokens.refreshTokenHash(retainedRefresh))).isTrue();
    }

    @Test
    void logoutRequiresAuthenticationAndReturns204ForValidSession() throws Exception {
        mvc.perform(delete("/api/auth/sessions/current").header("X-API-Version", "1"))
                .andExpect(status().isUnauthorized());
        String refresh = issueSession(null, Instant.now(clock).plus(30, ChronoUnit.DAYS));
        String access = accessForRefresh(refresh);
        mvc.perform(delete("/api/auth/sessions/current").header("X-API-Version", "1").header("Authorization", "Bearer " + access))
                .andExpect(status().isNoContent());
    }

    @Test
    void logoutRejectsAccessTokenWhoseSessionDoesNotExist() throws Exception {
        String forgedSessionAccess = tokens.issue(userId, Long.MAX_VALUE, Instant.now(clock));
        mvc.perform(delete("/api/auth/sessions/current").header("X-API-Version", "1")
                        .header("Authorization", "Bearer " + forgedSessionAccess))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("https://api.mindlog.com/problems/invalid-token"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder refresh(String token) {
        return post("/api/auth/sessions/refresh").header("X-API-Version", "1").contentType("application/json")
                .content("{\"refreshToken\":\"" + token + "\"}");
    }

    private String issueSession(Long deviceId, Instant expiresAt) {
        byte[] bytes = new byte[48]; new java.security.SecureRandom().nextBytes(bytes);
        String refresh = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update("INSERT INTO refresh_sessions(user_id,device_token_id,refresh_token_hash,expires_at,created_at) VALUES(?,?,?,?,CURRENT_TIMESTAMP)",
                userId, deviceId, tokens.refreshTokenHash(refresh), java.sql.Timestamp.from(expiresAt));
        return refresh;
    }

    private String registerDeviceSession(String deviceToken) {
        String refresh = issueDeviceSession(deviceToken);
        return accessForRefresh(refresh);
    }

    private String issueDeviceSession(String deviceToken) {
        Long deviceId = jdbc.queryForObject("INSERT INTO device_tokens(user_id,token,platform,created_at,updated_at) VALUES(?,?,'web',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) RETURNING id", Long.class, userId, deviceToken);
        return issueSession(deviceId, Instant.now(clock).plus(30, ChronoUnit.DAYS));
    }

    private String accessForRefresh(String refresh) {
        Long sessionId = jdbc.queryForObject("SELECT id FROM refresh_sessions WHERE refresh_token_hash=?", Long.class, tokens.refreshTokenHash(refresh));
        return tokens.issue(userId, sessionId, Instant.now(clock));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Database {
        @Bean @ServiceConnection PostgreSQLContainer postgres() { return new PostgreSQLContainer("postgres:16-alpine"); }
    }
}
