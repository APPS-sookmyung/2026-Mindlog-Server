package com.apps.mindlog.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.apps.mindlog.global.security.AccessTokenCodec;
import java.time.Instant;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Import(UserProfileApiTest.Database.class)
@TestPropertySource(properties = "mindlog.security.master-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
class UserProfileApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AccessTokenCodec tokens;
    private long userId;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM refresh_sessions");
        jdbc.update("DELETE FROM social_accounts");
        jdbc.update("DELETE FROM users");
        jdbc.update("INSERT INTO emotion_characters(id,code,name,display_order,active,created_at,updated_at) VALUES " +
                "(1,'anger','화남',1,true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)," +
                "(2,'neutral','보통',2,true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)," +
                "(3,'happiness','행복',3,true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)," +
                "(4,'sadness','슬픔',4,false,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) ON CONFLICT(id) DO UPDATE SET active=excluded.active,code=excluded.code,name=excluded.name");
        jdbc.update("""
                INSERT INTO users(email,password_hash,nickname,representative_character_id,current_emotion_code,
                    baseline_discomfort_score,gender,birth_year,onboarding_completed,onboarding_completed_at,
                    data_generation,account_status,after_reminder_enabled,weekly_report_start_day,created_at,updated_at)
                VALUES('profile@example.com','hash','Old Name',1,'sadness',60,'female',2000,true,
                    TIMESTAMPTZ '2026-11-23 10:00:00+00',0,'active',true,'sun',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """);
        userId = jdbc.queryForObject("SELECT id FROM users WHERE email='profile@example.com'", Long.class);
    }

    @Test
    void getMeReturnsAuthenticatedUsersStoredProfileAndProvider() throws Exception {
        String access = tokens.issue(userId, Instant.now());
        mvc.perform(get("/api/users/me").header("X-API-Version", "1").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.email").value("profile@example.com"))
                .andExpect(jsonPath("$.nickname").value("Old Name"))
                .andExpect(jsonPath("$.representativeCharacterId").value(1))
                .andExpect(jsonPath("$.representativeCharacter.code").value("anger"))
                .andExpect(jsonPath("$.currentEmotionCode").value("sadness"))
                .andExpect(jsonPath("$.gender").value("female"))
                .andExpect(jsonPath("$.loginProvider").value("email"))
                .andExpect(jsonPath("$.onboardingCompleted").value(true))
                .andExpect(jsonPath("$.onboardingCompletedAt").isNotEmpty())
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void patchUpdatesOnlyProvidedFieldsAndDoesNotTouchSameValues() throws Exception {
        String access = tokens.issue(userId, Instant.now());
        String before = jdbc.queryForObject("SELECT updated_at::text FROM users WHERE id=?", String.class, userId);
        mvc.perform(patch("/api/users/me/profile").header("X-API-Version", "1").header("Authorization", "Bearer " + access)
                        .contentType("application/json").content("{\"nickname\":\"  New Name  \"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nickname").value("New Name"))
                .andExpect(jsonPath("$.representativeCharacter.id").value(1));
        assertThat(jdbc.queryForObject("SELECT current_emotion_code FROM users WHERE id=?", String.class, userId)).isEqualTo("sadness");
        assertThat(jdbc.queryForObject("SELECT baseline_discomfort_score FROM users WHERE id=?", Integer.class, userId)).isEqualTo(60);
        mvc.perform(patch("/api/users/me/profile").header("X-API-Version", "1").header("Authorization", "Bearer " + access)
                        .contentType("application/json").content("{\"nickname\":\"New Name\"}"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT updated_at::text FROM users WHERE id=?", String.class, userId)).isNotEqualTo(before);
    }

    @Test
    void patchRejectsEmptyNullInvalidNicknameAndInactiveCharacter() throws Exception {
        String access = tokens.issue(userId, Instant.now());
        for (String body : new String[]{"{}", "{\"nickname\":null}", "{\"nickname\":\"   \"}",
                "{\"nickname\":\"bad\\nname\"}", "{\"nickname\":\"123456789012345678901\"}",
                "{\"representativeCharacterId\":4}"}) {
            mvc.perform(patch("/api/users/me/profile").header("X-API-Version", "1").header("Authorization", "Bearer " + access)
                            .contentType("application/json").content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.type").value("https://api.mindlog.com/problems/validation-error"));
        }
    }

    @Test
    void patchCanChangeActiveCharacterButDoesNotChangeCurrentEmotion() throws Exception {
        String access = tokens.issue(userId, Instant.now());
        MvcResult result = mvc.perform(patch("/api/users/me/profile").header("X-API-Version", "1").header("Authorization", "Bearer " + access)
                        .contentType("application/json").content("{\"representativeCharacterId\":3}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.representativeCharacter.code").value("happiness")).andReturn();
        assertThat(jdbc.queryForObject("SELECT representative_character_id FROM users WHERE id=?", Long.class, userId)).isEqualTo(3L);
        assertThat(jdbc.queryForObject("SELECT current_emotion_code FROM users WHERE id=?", String.class, userId)).isEqualTo("sadness");
        assertThat(result.getResponse().getContentAsString()).contains("updatedAt");
    }

    @Test
    void endpointsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/users/me").header("X-API-Version", "1")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/users/me/profile").header("X-API-Version", "1").contentType("application/json")
                        .content("{\"nickname\":\"Name\"}"))
                .andExpect(status().isUnauthorized());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Database {
        @Bean @ServiceConnection PostgreSQLContainer postgres() { return new PostgreSQLContainer("postgres:16-alpine"); }
    }
}
