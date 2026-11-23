package com.apps.mindlog.before;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.nullValue;

@SpringBootTest @AutoConfigureMockMvc @Transactional
@WithMockUser(username = "801") @Import(BeforeTestAdapters.class)
class BeforeLogApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void fixture() {
        jdbc.update("""
                INSERT INTO users(id,email,nickname,representative_character_id,account_status,onboarding_completed,
                    after_reminder_enabled,weekly_report_start_day,created_at,updated_at)
                VALUES(801,'before-' || gen_random_uuid() || '@example.com','테스트',1,'active',true,true,'sun',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                ON CONFLICT (id) DO NOTHING
                """);
        jdbc.update("""
                INSERT INTO ai_jobs(id,user_id,before_log_id,after_log_id,kind,status,request_key,input_version,
                    data_generation,input_snapshot,retryable,created_at,updated_at)
                VALUES(?,801,NULL,NULL,'before_rebuttal','COMPLETED',?,1,0,
                    '{"situationTypeId":1,"scheduledAt":"%s","expectedScore":80,"worstScenario":"발표를 망칠 것 같다.","title":null,"description":null}'::jsonb,
                    false,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """.formatted(LocalDate.now().plusDays(1)), UUID.fromString("10000000-0000-0000-0000-000000000801"),
                UUID.fromString("10000000-0000-0000-0000-000000000802"));
    }

    @Test void createsRecordWithSpecResponseAndForeignKeys() throws Exception {
        mvc.perform(post("/api/before-logs").header("X-API-Version", "1")
                        .header("Idempotency-Key", "before-create-801")
                        .contentType("application/json")
                        .content("""
                                {"situationTypeId":1,"scheduledAt":"%s","expectedScore":80,
                                 "worstScenario":" 발표를 망칠 것 같다. ","title":null,"description":null,
                                 "rebuttalJobId":"10000000-0000-0000-0000-000000000801","inputVersion":1}
                                """.formatted(LocalDate.now().plusDays(1))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/before-logs/")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.situationTypeId").value(1))
                .andExpect(jsonPath("$.situationTypeName").value("발표"))
                .andExpect(jsonPath("$.scheduledAt").value(LocalDate.now().plusDays(1).toString()))
                .andExpect(jsonPath("$.expectedScore").value(80))
                .andExpect(jsonPath("$.worstScenario").value("발표를 망칠 것 같다."))
                .andExpect(jsonPath("$.title").value("발표"))
                .andExpect(jsonPath("$.description").value(nullValue()))
                .andExpect(jsonPath("$.hasAfter").value(false))
                .andExpect(jsonPath("$.afterLogId").value(nullValue()))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
        var row = jdbc.queryForMap("SELECT id,user_id,situation_type_id FROM before_logs WHERE user_id=801");
        assertThat(((Number) row.get("user_id")).longValue()).isEqualTo(801L);
        assertThat(((Number) row.get("situation_type_id")).longValue()).isEqualTo(1L);
        mvc.perform(post("/api/before-logs").header("X-API-Version", "1")
                        .header("Idempotency-Key", "before-create-801")
                        .contentType("application/json")
                        .content("""
                                {"situationTypeId":1,"scheduledAt":"%s","expectedScore":80,
                                 "worstScenario":" 발표를 망칠 것 같다. ","title":null,"description":null,
                                 "rebuttalJobId":"10000000-0000-0000-0000-000000000801","inputVersion":1}
                                """.formatted(LocalDate.now().plusDays(1))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(((Number) row.get("id")).longValue()));

    }

    @Test void invalidScoreIsValidationError() throws Exception {
        mvc.perform(post("/api/before-logs").header("X-API-Version", "1")
                        .header("Idempotency-Key", "before-invalid-score")
                        .contentType("application/json")
                        .content("""
                                {"situationTypeId":1,"scheduledAt":"%s","expectedScore":101,
                                 "worstScenario":"발표를 망칠 것 같다.","rebuttalJobId":"10000000-0000-0000-0000-000000000801","inputVersion":1}
                                """.formatted(LocalDate.now().plusDays(1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://api.mindlog.com/problems/validation-error"));
    }

    @Test void situationAndBlankWorstScenarioAreValidationErrors() throws Exception {
        invalidBody(999, "scenario");
        jdbc.update("UPDATE situation_types SET active=false WHERE id=2");
        invalidBody(2, "scenario");
        invalidBody(1, "   ");
        jdbc.update("UPDATE situation_types SET active=true WHERE id=2");
    }

    private void invalidBody(long situationTypeId, String scenario) throws Exception {
        String body = """
                {"situationTypeId":%d,"scheduledAt":"%s","expectedScore":50,
                 "worstScenario":"%s","rebuttalJobId":"10000000-0000-0000-0000-000000000801","inputVersion":1}
                """.formatted(situationTypeId, LocalDate.now().plusDays(1), scenario);
        mvc.perform(post("/api/before-logs").header("X-API-Version", "1")
                        .header("Idempotency-Key", "before-invalid-" + UUID.randomUUID())
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://api.mindlog.com/problems/validation-error"));
    }
}
