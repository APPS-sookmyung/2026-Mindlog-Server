package com.apps.mindlog.ai.after;

import com.apps.mindlog.after.AfterTestAdapters;
import com.apps.mindlog.after.dto.response.AnalysisResult;
import com.apps.mindlog.ai.job.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"mindlog.ai.max-requests-per-hour=20","mindlog.ai.dispatch-enabled=false"})
@AutoConfigureMockMvc @WithMockUser(username="900") @Import({AfterTestAdapters.class,WorkerTestClient.class})
class AfterAnalysisWorkerTest {
    @Autowired JdbcTemplate jdbc; @Autowired MockMvc mvc; @Autowired JsonMapper json;
    @Autowired JobExecutor executor; @Autowired JobStore jobs; @Autowired WorkerTestClient.FakeClient client;
    @BeforeEach void setup(){
        cleanup();client.reset();
        jdbc.update("INSERT INTO users(id,email,nickname,representative_character_id,account_status,onboarding_completed,after_reminder_enabled,weekly_report_start_day,created_at,updated_at) VALUES(900,'worker@example.com','예선',1,'active',true,true,'sun',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO before_logs(id,user_id,situation_type_id,scheduled_at,expected_score,worst_scenario,created_at,updated_at) VALUES(900,900,1,'2026-09-30',80,'걱정',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO after_logs(id,before_log_id,free_writing,record_status,created_at,updated_at) VALUES(900,900,'모두 나를 싫어할 것이다','DRAFT',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
    }
    @AfterEach void cleanup(){jdbc.update("DELETE FROM users WHERE id=900");}
    UUID start()throws Exception{
        var body=mvc.perform(post("/api/after-logs/900/analyses").header("X-API-Version","1").header("Idempotency-Key",UUID.randomUUID().toString()).contentType("application/json").content("{\"inputVersion\":1}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(body).get("jobId").asText());
    }
    AnalysisResult normal(){return new AnalysisResult("상황을 돌아봐요",0,List.of(),List.of(new AnalysisResult.Card("돌아보기",null,"돌아보기","실제로 일어난 일을 살펴봐요")));}
    @Test void completesOnceWithProvenanceAndGeneralCardWithoutCatalog()throws Exception{
        client.answer=r->{assertThat(r.inputJson()).contains("allowedDistortions","모두 나를");return normal();};
        var id=start();executor.run(id);executor.run(id);
        assertThat(jobs.find(id).orElseThrow().status()).isEqualTo(JobStatus.COMPLETED);
        assertThat(client.calls.get()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT model_version FROM af_ai_feedbacks WHERE after_log_id=900",String.class)).isEqualTo("test-model");
        assertThat(jdbc.queryForObject("SELECT input_snapshot::text FROM af_ai_feedbacks WHERE after_log_id=900",String.class)).contains("모두 나를");
        assertThat(jobs.find(id).orElseThrow().result()).doesNotContain("inputSnapshot","modelVersion");
        mvc.perform(get("/api/after-logs/900/analysis").header("X-API-Version","1")).andExpect(status().isOk()).andExpect(jsonPath("$.analysisStatus").value("COMPLETED"));
    }
    @Test void invalidOutputFailsBothJobAndFeedback()throws Exception{
        client.answer=r->new AnalysisResult("요약",1,List.of(new AnalysisResult.Distortion(999,"없는 태그","만들어낸 인용","설명")),normal().analysisCards());
        var id=start();executor.run(id);
        assertThat(jobs.find(id).orElseThrow().failureReason()).isEqualTo("INVALID_OUTPUT");
        assertThat(jdbc.queryForObject("SELECT analysis_status FROM af_ai_feedbacks WHERE after_log_id=900",String.class)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM after_analysis_cards",Integer.class)).isZero();
    }
    @Test void providerFailureIsSafeAndMarksFeedbackFailed()throws Exception{
        var id=start();executor.run(id);
        assertThat(jobs.find(id).orElseThrow().failureReason()).isEqualTo("PROVIDER_UNAVAILABLE");
        assertThat(jdbc.queryForObject("SELECT analysis_status FROM af_ai_feedbacks WHERE after_log_id=900",String.class)).isEqualTo("FAILED");
    }
    @Test void generationChangeDuringCallDiscardsResult()throws Exception{
        client.answer=r->{jdbc.update("UPDATE users SET data_generation=1 WHERE id=900");return normal();};
        var id=start();executor.run(id);
        assertThat(jobs.find(id).orElseThrow().failureReason()).isEqualTo("STALE_INPUT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM after_analysis_cards",Integer.class)).isZero();
    }
    @Test void withdrawalDuringCallDoesNotRecreateDeletedData()throws Exception{
        client.answer=r->{cleanup();return normal();};var id=start();executor.run(id);
        assertThat(jobs.find(id)).isEmpty();assertThat(jdbc.queryForObject("SELECT count(*) FROM af_ai_feedbacks",Integer.class)).isZero();
    }
}
