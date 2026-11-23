package com.apps.mindlog.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(EmailLoginApiTest.Database.class)
@org.springframework.test.context.TestPropertySource(properties =
        "mindlog.security.master-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
class EmailLoginApiTest {
    private static final BCryptPasswordEncoder PASSWORDS = new BCryptPasswordEncoder();
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.apps.mindlog.global.security.AccessTokenCodec tokens;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM refresh_sessions");
        jdbc.update("DELETE FROM device_tokens");
        jdbc.update("DELETE FROM email_login_attempts");
        jdbc.update("DELETE FROM users");
        jdbc.update("INSERT INTO emotion_characters(id,code,name,display_order,active,created_at,updated_at) VALUES(1,'anger','테스트',1,true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) ON CONFLICT(id) DO NOTHING");
    }

    @Test
    void successfulLoginNormalizesEmailAndReturnsTokensAndOnboardingState() throws Exception {
        insertUser("member@example.com", PASSWORDS.encode("Password123!"), "active", true);
        MvcResult result = mvc.perform(login("  MEMBER@Example.COM ", "Password123!"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.onboardingCompleted").value(true))
                .andReturn();
        String accessToken = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
        assertThat(tokens.verify(accessToken, java.time.Clock.systemUTC()).userId()).isPositive();
        Integer sessions = jdbc.queryForObject("SELECT count(*) FROM refresh_sessions", Integer.class);
        assertThat(sessions).isEqualTo(1);
    }

    @Test
    void missingWrongDeletedAndGoogleOnlyAccountsHaveSameUnauthorizedProblem() throws Exception {
        insertUser("wrong@example.com", PASSWORDS.encode("Correct123!"), "active", false);
        insertUser("deleted@example.com", PASSWORDS.encode("Correct123!"), "deleting", false);
        insertUser("google@example.com", null, "active", false);
        String[] emails = {"missing@example.com", "wrong@example.com", "deleted@example.com", "google@example.com"};
        String expectedType = "https://api.mindlog.com/problems/invalid-credentials";
        for (String email : emails) {
            mvc.perform(login(email, "Incorrect123!"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.type").value(expectedType))
                    .andExpect(jsonPath("$.detail").value("이메일 또는 비밀번호를 확인해 주세요."));
        }
    }

    @Test
    void fifthFailureLocksEmailAndReturnsRetryAfterThenSuccessClearsFailures() throws Exception {
        insertUser("throttle@example.com", PASSWORDS.encode("Correct123!"), "active", false);
        for (int i = 0; i < 5; i++) mvc.perform(login("throttle@example.com", "Wrong123!"))
                .andExpect(status().isUnauthorized());
        mvc.perform(login("THROTTLE@example.com", "Wrong123!"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.type").value("https://api.mindlog.com/problems/login-rate-limited"));

        jdbc.update("UPDATE email_login_attempts SET login_locked_until=CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE email_key=?",
                tokens.fingerprint("email-login:throttle@example.com"));
        mvc.perform(login("throttle@example.com", "Correct123!"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboardingCompleted").value(false));
        assertThat(jdbc.queryForObject("SELECT failed_login_count FROM email_login_attempts WHERE email_key=?",
                Integer.class, tokens.fingerprint("email-login:throttle@example.com"))).isZero();
        assertThat(jdbc.queryForObject("SELECT failed_login_count FROM users WHERE lower(email)='throttle@example.com'", Integer.class)).isZero();
    }

    @Test
    void deviceTokenIsAssociatedWithTheNewRefreshSession() throws Exception {
        insertUser("device@example.com", PASSWORDS.encode("Password123!"), "active", false);
        mvc.perform(post("/api/auth/email-sessions").header("X-API-Version", "1").contentType("application/json")
                        .content("""
                                {"email":"device@example.com","password":"Password123!","deviceToken":"device-token"}
                                """))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM device_tokens WHERE token='device-token'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_sessions WHERE device_token_id IS NOT NULL", Integer.class)).isEqualTo(1);
    }

    private MockHttpServletRequestBuilder login(String email, String password) {
        return post("/api/auth/email-sessions").header("X-API-Version", "1").contentType("application/json")
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
    }

    private void insertUser(String email, String passwordHash, String status, boolean onboardingCompleted) {
        jdbc.update("""
                INSERT INTO users(email,password_hash,nickname,representative_character_id,account_status,onboarding_completed,
                    after_reminder_enabled,weekly_report_start_day,created_at,updated_at)
                VALUES(?,?, '테스트',1,?,?,true,'sun',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, email, passwordHash, status, onboardingCompleted);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Database {
        @Bean @ServiceConnection PostgreSQLContainer postgres() { return new PostgreSQLContainer("postgres:16-alpine"); }
    }
}
