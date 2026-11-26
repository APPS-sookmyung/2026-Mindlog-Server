package com.apps.mindlog.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "mindlog.security.master-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
@AutoConfigureMockMvc
@Import(EmailAvailabilityApiTest.Database.class)
@Transactional
class EmailAvailabilityApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AtomicReference<Instant> now;

    @Test
    void normalizesEmailAndReportsAvailabilityAcrossCaseAndWhitespace() throws Exception {
        mvc.perform(get("/api/auth/email-availability").param("email", "  NEW.User@Example.com  ")
                        .header("X-API-Version", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("new.user@example.com"))
                .andExpect(jsonPath("$.available").value(true));

        insertUser("Known.User@example.com");
        mvc.perform(get("/api/auth/email-availability").param("email", " known.user@EXAMPLE.COM ")
                        .header("X-API-Version", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("known.user@example.com"))
                .andExpect(jsonPath("$.available").value(false));
    }

    @Test
    void invalidOrMissingEmailReturnsValidationProblem() throws Exception {
        for (String email : new String[]{"", "not-an-email", "x".repeat(255) + "@example.com"}) {
            mvc.perform(get("/api/auth/email-availability").param("email", email)
                            .header("X-API-Version", "1"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.type").value("https://api.mindlog.com/problems/validation-error"));
        }
        mvc.perform(get("/api/auth/email-availability").header("X-API-Version", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://api.mindlog.com/problems/validation-error"));
    }

    @Test
    void endpointIsPublicAndRequiresApiVersion() throws Exception {
        mvc.perform(get("/api/auth/email-availability").param("email", "a@example.com")
                        .header("X-API-Version", "1"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/auth/email-availability").param("email", "a@example.com"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void limitsRequestsPerIpAndReturnsRetryAfter() throws Exception {
        now.set(Instant.parse("2026-11-25T02:10:10Z"));
        for (int i = 0; i < 10; i++) {
            mvc.perform(get("/api/auth/email-availability").param("email", "a@example.com")
                            .header("X-API-Version", "1"))
                    .andExpect(status().isOk());
        }
        mvc.perform(get("/api/auth/email-availability").param("email", "a@example.com")
                        .header("X-API-Version", "1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.type").value("https://api.mindlog.com/problems/rate-limited"));
    }

    @Test
    void resetsRequestCountWhenClockAdvancesToNextWindow() throws Exception {
        now.set(Instant.parse("2026-11-25T02:10:10Z"));
        for (int i = 0; i < 10; i++) {
            mvc.perform(get("/api/auth/email-availability").param("email", "b@example.com")
                            .header("X-API-Version", "1"))
                    .andExpect(status().isOk());
        }
        now.set(Instant.parse("2026-11-25T02:11:00Z"));
        mvc.perform(get("/api/auth/email-availability").param("email", "b@example.com")
                        .header("X-API-Version", "1"))
                .andExpect(status().isOk());
    }

    private void insertUser(String email) {
        jdbc.update("""
                INSERT INTO users(email,nickname,representative_character_id,account_status,
                    after_reminder_enabled,weekly_report_start_day,created_at,updated_at)
                VALUES (?, '테스트', 1, 'onboarding', true, 'sun', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, email);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Database {
        @Bean @ServiceConnection PostgreSQLContainer postgres() { return new PostgreSQLContainer("postgres:16-alpine"); }
        @Bean AtomicReference<Instant> now() { return new AtomicReference<>(Instant.parse("2026-11-25T02:10:10Z")); }
        @Bean @Primary Clock testClock(AtomicReference<Instant> now) {
            return new Clock() {
                @Override public ZoneId getZone() { return ZoneId.of("Asia/Seoul"); }
                @Override public Clock withZone(ZoneId zone) { return this; }
                @Override public Instant instant() { return now.get(); }
            };
        }
    }
}
