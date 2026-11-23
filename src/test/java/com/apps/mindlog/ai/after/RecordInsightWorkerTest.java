package com.apps.mindlog.ai.after;

import com.apps.mindlog.after.AfterTestAdapters;
import com.apps.mindlog.ai.job.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"mindlog.ai.max-requests-per-hour=20","mindlog.ai.dispatch-enabled=false"})
@AutoConfigureMockMvc @WithMockUser(username="900") @Import({AfterTestAdapters.class,WorkerTestClient.class})
class RecordInsightWorkerTest {
    @Autowired JdbcTemplate jdbc; @Autowired MockMvc mvc;
    @Autowired JobExecutor executor; @Autowired JobStore jobs; @Autowired WorkerTestClient.FakeClient client;
    @BeforeEach void setup(){
        cleanup();client.reset();
        for(int user=900;user<=901;user++)jdbc.update("INSERT INTO users(id,email,nickname,representative_character_id,account_status,onboarding_completed,after_reminder_enabled,weekly_report_start_day,created_at,updated_at) VALUES(?,?,'예선',1,'active',true,true,'sun',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",user,user+"@insight.example");
        before(900,900,1,80);
        jdbc.update("INSERT INTO after_logs(id,before_log_id,free_writing,record_status,created_at,updated_at) VALUES(900,900,'내 원문','DRAFT',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO af_ai_feedbacks(after_log_id,analysis_status,input_version,summary,completed_at,created_at,updated_at) VALUES(900,'COMPLETED',1,'서버 분석',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO ai_jobs(id,user_id,after_log_id,kind,status,request_key,input_version,data_generation,input_snapshot,result,retryable,completed_at,created_at,updated_at) VALUES(?,900,900,'diary_draft','COMPLETED',?,1,0,'{}','{\"content\":\"초안\"}',false,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",UUID.randomUUID(),UUID.randomUUID());
    }
    @AfterEach void cleanup(){jdbc.update("DELETE FROM users WHERE id IN (900,901)");}
    void before(long id,long user,int situation,int expected){jdbc.update("INSERT INTO before_logs(id,user_id,situation_type_id,scheduled_at,expected_score,worst_scenario,created_at,updated_at) VALUES(?,?,?,'2026-09-30',?,'걱정',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",id,user,situation,expected);}
    void prior(long id,long user,int situation,int expected,int actual){
        before(id,user,situation,expected);
        jdbc.update("INSERT INTO after_logs(id,before_log_id,free_writing,record_status,final_diary,actual_score,finalized_at,created_at,updated_at) VALUES(?,?,'과거','FINALIZED','과거 일기',?,CURRENT_TIMESTAMP-INTERVAL '1 day',CURRENT_TIMESTAMP-INTERVAL '1 day',CURRENT_TIMESTAMP)",id,id,actual);
    }
    UUID confirm()throws Exception{
        mvc.perform(put("/api/after-logs/900/final-diary").header("X-API-Version","1").header("Idempotency-Key","confirm").contentType("application/json").content("{\"inputVersion\":1,\"finalDiary\":\"최종 원문\",\"actualScore\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.insightStatus").value("PENDING"));
        return jdbc.queryForObject("SELECT id FROM ai_jobs WHERE after_log_id=900 AND kind='record_insight'",UUID.class);
    }
    void validAnswer(){client.answer=r->new RecordInsightWorker.Reflection("걱정했던 일과 실제 경험은 어떻게 달랐나요?");}
    @Test void finalSaveQueuesOnceAndWorkerSavesThreeCardsWithoutReadRegeneration()throws Exception{
        validAnswer();var id=confirm();assertThat(confirm()).isEqualTo(id);executor.run(id);executor.run(id);
        assertThat(client.calls.get()).isEqualTo(1);assertThat(jobs.find(id).orElseThrow().status()).isEqualTo(JobStatus.COMPLETED);
        var contents=jdbc.queryForList("SELECT insight_content FROM ai_insights WHERE after_log_id=900 ORDER BY display_order",String.class);
        assertThat(contents).hasSize(3);assertThat(contents.get(0)).contains("예상 80점, 실제 0점","80점 낮았어요");assertThat(contents.get(1)).contains("아직 없어요");
        for(int i=0;i<2;i++)mvc.perform(get("/api/after-logs/900").header("X-API-Version","1")).andExpect(status().isOk());
        assertThat(client.calls.get()).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT used FROM llm_quota_usage WHERE user_id=900",Integer.class)).isEqualTo(1);
    }
    @Test void historyIncludesOnlyOwnedSameSituationPastFinalizedRecords()throws Exception{
        prior(910,900,1,40,0);prior(911,900,1,20,40);
        prior(912,901,1,100,0);prior(913,900,2,100,0);prior(914,900,1,100,0);
        jdbc.update("UPDATE after_logs SET finalized_at=CURRENT_TIMESTAMP+INTERVAL '1 day' WHERE id=914");
        before(915,900,1,100);jdbc.update("INSERT INTO after_logs(id,before_log_id,free_writing,record_status,created_at,updated_at) VALUES(915,915,'작성 중','DRAFT',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        client.answer=r->{assertThat(r.inputJson()).contains("\"count\":2");return new RecordInsightWorker.Reflection("어떤 차이가 있었나요?");};
        var id=confirm();executor.run(id);
        assertThat(jobs.find(id).orElseThrow().status()).isEqualTo(JobStatus.COMPLETED);
        assertThat(jdbc.queryForObject("SELECT insight_content FROM ai_insights WHERE after_log_id=900 AND insight_type='situation_pattern'",String.class)).contains("2개 기록","평균 10점 높았어요");
    }
    @Test void quotaExhaustionDoesNotUndoFinalRecordOrCallModel()throws Exception{
        jdbc.update("INSERT INTO llm_quota_usage(user_id,bucket_start,used) VALUES(900,date_trunc('hour',CURRENT_TIMESTAMP),20)");
        validAnswer();var id=confirm();executor.run(id);
        assertThat(jobs.find(id).orElseThrow().failureReason()).isEqualTo("RATE_LIMITED");assertThat(client.calls.get()).isZero();assertFinalized();
    }
    @Test void providerFailurePreservesFinalRecord()throws Exception{
        var id=confirm();executor.run(id);assertThat(jobs.find(id).orElseThrow().failureReason()).isEqualTo("PROVIDER_UNAVAILABLE");assertFinalized();
    }
    @Test void invalidQuestionDoesNotPartiallyStoreCards()throws Exception{
        client.answer=r->new RecordInsightWorker.Reflection(" ");var id=confirm();executor.run(id);
        assertThat(jobs.find(id).orElseThrow().failureReason()).isEqualTo("INVALID_OUTPUT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_insights",Integer.class)).isZero();assertFinalized();
    }
    @Test void resetDuringProviderCallDiscardsAllCards()throws Exception{
        client.answer=r->{jdbc.update("UPDATE users SET data_generation=1 WHERE id=900");return new RecordInsightWorker.Reflection("어땠나요?");};
        var id=confirm();executor.run(id);assertThat(jobs.find(id).orElseThrow().failureReason()).isEqualTo("STALE_INPUT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_insights",Integer.class)).isZero();
    }
    void assertFinalized(){assertThat(jdbc.queryForObject("SELECT record_status FROM after_logs WHERE id=900",String.class)).isEqualTo("FINALIZED");assertThat(jdbc.queryForObject("SELECT actual_score FROM after_logs WHERE id=900",Integer.class)).isZero();}
}
