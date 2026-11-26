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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.nullValue;

@SpringBootTest(properties = "mindlog.security.master-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=") @AutoConfigureMockMvc @Transactional
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
                INSERT INTO users(id,email,nickname,representative_character_id,account_status,onboarding_completed,
                    after_reminder_enabled,weekly_report_start_day,created_at,updated_at)
                VALUES(802,'before-foreign-' || gen_random_uuid() || '@example.com','타인',1,'active',true,true,'sun',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                ON CONFLICT (id) DO NOTHING
                """);
        jdbc.update("""
                INSERT INTO ai_jobs(id,user_id,before_log_id,after_log_id,kind,status,request_key,input_version,
                    data_generation,input_snapshot,result,retryable,completed_at,expires_at,created_at,updated_at)
                VALUES(?,801,NULL,NULL,'before_rebuttal','COMPLETED',?,1,0,
                    '{"situationTypeId":1,"scheduledAt":"%s","expectedScore":80,"worstScenario":"발표를 망칠 것 같다.","title":null,"description":null}'::jsonb,
                    '{"feedbackContent":"예상보다 괜찮을 수 있어요.","similarRecords":[],"similarRecordsComment":null}'::jsonb,
                    false,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP + INTERVAL '30 minutes',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
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
        assertThat(jdbc.queryForObject("SELECT feedback_content FROM bf_ai_feedbacks WHERE before_log_id=?",
                String.class, ((Number) row.get("id")).longValue())).isEqualTo("예상보다 괜찮을 수 있어요.");
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

    @Test void detailReturnsOwnedBeforeFieldsAndStoredRebuttal() throws Exception {
        long id = insertBefore(801);
        jdbc.update("INSERT INTO bf_ai_feedbacks(before_log_id,feedback_content,model_version,created_at,updated_at) VALUES(?,?,'test',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                id, "결과가 생각보다 괜찮을 수 있어요.");

        mvc.perform(get("/api/before-logs/{id}", id).header("X-API-Version", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.situationTypeName").value("발표"))
                .andExpect(jsonPath("$.expectedScore").value(70))
                .andExpect(jsonPath("$.rebuttal.feedbackContent").value("결과가 생각보다 괜찮을 수 있어요."))
                .andExpect(jsonPath("$.hasAfter").value(false))
                .andExpect(jsonPath("$.afterLogId").value(nullValue()))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test void detailHidesMissingAndForeignRecordsAndReturnsNullRebuttal() throws Exception {
        long own = insertBefore(801);
        long foreign = insertBefore(802);
        mvc.perform(get("/api/before-logs/{id}", own).header("X-API-Version", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rebuttal").value(nullValue()));
        mvc.perform(get("/api/before-logs/{id}", foreign).header("X-API-Version", "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://api.mindlog.com/problems/resource-not-found"));
        mvc.perform(get("/api/before-logs/{id}", 999999).header("X-API-Version", "1"))
                .andExpect(status().isNotFound());
    }

    @Test @org.springframework.security.test.context.support.WithAnonymousUser
    void detailRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/before-logs/{id}", 1).header("X-API-Version", "1"))
                .andExpect(status().isUnauthorized());
    }

    private long insertBefore(long userId) {
        long beforeId = jdbc.queryForObject("""
                INSERT INTO before_logs(user_id,situation_type_id,title,description,scheduled_at,expected_score,
                    worst_scenario,created_at,updated_at)
                VALUES(?,1,'발표','설명',CURRENT_DATE,70,'최악의 상황',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                RETURNING id
                """, Long.class, userId);
        return beforeId;
    }

    private void insertAfter(long beforeId, String status) {
        if ("FINALIZED".equals(status)) {
            jdbc.update("""
                    INSERT INTO after_logs(before_log_id,actual_score,free_writing,final_diary,record_status,
                        input_version,finalized_at,created_at,updated_at)
                    VALUES(?,60,'기록 내용','최종 일기','FINALIZED',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                    """, beforeId);
            return;
        }
        jdbc.update("""
                INSERT INTO after_logs(before_log_id,free_writing,record_status,input_version,created_at,updated_at)
                VALUES(?, '기록 내용', 'DRAFT', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, beforeId);
    }

    @Test void detailReturnsDraftAfterIdButOnlyFinalizedCountsAsAfter() throws Exception {
        long draftBeforeId = insertBefore(801);
        insertAfter(draftBeforeId, "DRAFT");
        mvc.perform(get("/api/before-logs/{id}", draftBeforeId).header("X-API-Version", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasAfter").value(false))
                .andExpect(jsonPath("$.afterLogId").isNumber());

        long finalizedBeforeId = insertBefore(801);
        insertAfter(finalizedBeforeId, "FINALIZED");
        mvc.perform(get("/api/before-logs/{id}", finalizedBeforeId).header("X-API-Version", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasAfter").value(true))
                .andExpect(jsonPath("$.afterLogId").isNumber());
    }

    @Test void listFiltersFinalizedAfterAndKeepsDraftIdAndSortsNewestFirst() throws Exception {
        long noAfter = insertBefore(801);
        long draft = insertBefore(801);
        insertAfter(draft, "DRAFT");
        long finalized = insertBefore(801);
        insertAfter(finalized, "FINALIZED");
        long foreign = insertBefore(802);

        mvc.perform(get("/api/before-logs").header("X-API-Version", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[0].id").value(finalized))
                .andExpect(jsonPath("$.content[1].id").value(draft))
                .andExpect(jsonPath("$.content[1].hasAfter").value(false))
                .andExpect(jsonPath("$.content[1].afterLogId").isNumber())
                .andExpect(jsonPath("$.content[2].id").value(noAfter))
                .andExpect(jsonPath("$.content[*].id").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(foreign))))
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.page.totalPages").value(1));

        mvc.perform(get("/api/before-logs").param("hasAfter", "true").header("X-API-Version", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(finalized))
                .andExpect(jsonPath("$.content[0].hasAfter").value(true));

        mvc.perform(get("/api/before-logs").param("hasAfter", "false").header("X-API-Version", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(draft))
                .andExpect(jsonPath("$.content[1].id").value(noAfter));

        assertThat(noAfter).isNotEqualTo(foreign);
    }

    @Test void listValidatesPagingAndRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/before-logs").param("page", "-1").header("X-API-Version", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://api.mindlog.com/problems/validation-error"));
        mvc.perform(get("/api/before-logs").param("size", "0").header("X-API-Version", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://api.mindlog.com/problems/validation-error"));
        mvc.perform(get("/api/before-logs").param("size", "101").header("X-API-Version", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.size").value(100));
        mvc.perform(get("/api/before-logs").param("page", "21474837").param("size", "100")
                        .header("X-API-Version", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://api.mindlog.com/problems/validation-error"));
        mvc.perform(get("/api/before-logs").header("X-API-Version", "1")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous()))
                .andExpect(status().isUnauthorized());
    }

    @Test void listSortsByCreatedAtBeforeIdWhenThoseOrdersDiffer() throws Exception {
        long smallerId = insertBefore(801);
        long largerId = insertBefore(801);
        jdbc.update("UPDATE before_logs SET created_at=TIMESTAMPTZ '2025-01-02 00:00:00+00' WHERE id=?", smallerId);
        jdbc.update("UPDATE before_logs SET created_at=TIMESTAMPTZ '2025-01-01 00:00:00+00' WHERE id=?", largerId);

        var result = mvc.perform(get("/api/before-logs").header("X-API-Version", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(smallerId))
                .andExpect(jsonPath("$.content[1].id").value(largerId))
                .andReturn();
        String firstCreatedAt = com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.content[0].createdAt");
        String secondCreatedAt = com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.content[1].createdAt");
        assertThat(java.time.OffsetDateTime.parse(firstCreatedAt))
                .isAfter(java.time.OffsetDateTime.parse(secondCreatedAt));
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
