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
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"mindlog.ai.max-requests-per-hour=20","mindlog.ai.dispatch-enabled=false"})
@AutoConfigureMockMvc @WithMockUser(username="900") @Import({AfterTestAdapters.class,WorkerTestClient.class})
class DiaryDraftTest {
    @Autowired JdbcTemplate jdbc; @Autowired MockMvc mvc; @Autowired JsonMapper json;
    @Autowired JobExecutor executor; @Autowired JobStore jobs; @Autowired WorkerTestClient.FakeClient client;
    @BeforeEach void setup(){
        cleanup();client.reset();
        for(int id=900;id<=901;id++){
            jdbc.update("INSERT INTO users(id,email,nickname,representative_character_id,account_status,onboarding_completed,after_reminder_enabled,weekly_report_start_day,created_at,updated_at) VALUES(?,?,'예선',1,'active',true,true,'sun',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",id,id+"@draft.example");
            jdbc.update("INSERT INTO before_logs(id,user_id,situation_type_id,scheduled_at,expected_score,worst_scenario,created_at,updated_at) VALUES(?,?,1,'2026-09-30',80,'걱정',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",id,id);
            jdbc.update("INSERT INTO after_logs(id,before_log_id,free_writing,record_status,created_at,updated_at) VALUES(?,?,'내 원문','DRAFT',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",id,id);
        }
        jdbc.update("INSERT INTO af_ai_feedbacks(after_log_id,analysis_status,input_version,summary,completed_at,created_at,updated_at) VALUES(900,'COMPLETED',1,'서버 분석',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
    }
    @AfterEach void cleanup(){jdbc.update("DELETE FROM users WHERE id IN (900,901)");}
    ResultActions request(long id,String key,String body)throws Exception{
        var req=post("/api/after-logs/"+id+"/diary-drafts").header("X-API-Version","1").contentType("application/json").content(body);
        if(key!=null)req.header("Idempotency-Key",key);return mvc.perform(req);
    }
    String start(String key)throws Exception{return request(900,key,"{\"inputVersion\":1}").andExpect(status().isAccepted()).andExpect(header().string("Retry-After","2")).andReturn().getResponse().getContentAsString();}
    UUID id(String response){return UUID.fromString(json.readTree(response).get("jobId").asText());}
    ResultActions poll(long afterId,UUID id)throws Exception{return mvc.perform(get("/api/after-logs/"+afterId+"/diary-drafts/"+id).header("X-API-Version","1"));}
    @Test void generatesTemporaryDraftWithoutFinalizingAndCanFinalizeAfterward()throws Exception{
        client.answer=r->{assertThat(r.inputJson()).contains("내 원문","서버 분석");return new DiaryDraftResult("  오늘의 일기  ");};
        var id=id(start("one"));executor.run(id);executor.run(id);
        poll(900,id).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED")).andExpect(jsonPath("$.draft.content").value("오늘의 일기")).andExpect(jsonPath("$.inputVersion").value(1));
        var job=jobs.find(id).orElseThrow();assertThat(job.expiresAt()).isEqualTo(job.completedAt().plusSeconds(1800));
        assertThat(client.calls.get()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT final_diary FROM after_logs WHERE id=900",String.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT actual_score FROM after_logs WHERE id=900",Integer.class)).isNull();
        mvc.perform(put("/api/after-logs/900/final-diary").header("X-API-Version","1").header("Idempotency-Key","final").contentType("application/json").content("{\"inputVersion\":1,\"finalDiary\":\"직접 고친 일기\",\"actualScore\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.recordStatus").value("FINALIZED")).andExpect(jsonPath("$.actualScore").value(0));
    }
    @Test void replayReturnsSame202AndConsumesOnceWhileDifferentActiveKeyConflicts()throws Exception{
        var body=start("same");assertThat(start("same")).isEqualTo(body);
        assertThat(jdbc.queryForObject("SELECT used FROM llm_quota_usage WHERE user_id=900",Integer.class)).isEqualTo(1);
        request(900,"other","{\"inputVersion\":1}").andExpect(status().isConflict());
        request(900,"same","{\"inputVersion\":2}").andExpect(status().isConflict()).andExpect(jsonPath("$.type").value("https://api.mindlog.com/problems/idempotency-conflict"));
    }
    @Test void staleVersionAndIncompleteAnalysisDoNotConsumeQuota()throws Exception{
        request(900,"stale","{\"inputVersion\":2}").andExpect(status().isConflict()).andExpect(jsonPath("$.type").value("https://api.mindlog.com/problems/stale-input-version"));
        jdbc.update("UPDATE af_ai_feedbacks SET analysis_status='FAILED' WHERE after_log_id=900");
        request(900,"notready","{\"inputVersion\":1}").andExpect(status().isConflict()).andExpect(jsonPath("$.type").value("https://api.mindlog.com/problems/analysis-not-ready"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM llm_quota_usage",Integer.class)).isZero();
    }
    @Test void privatePathsExpiredDraftAndOldGenerationAreNotFound()throws Exception{
        request(901,"foreign","{\"inputVersion\":1}").andExpect(status().isNotFound());
        client.answer=r->new DiaryDraftResult("일기");var id=id(start("one"));executor.run(id);
        poll(901,id).andExpect(status().isNotFound());poll(900,UUID.randomUUID()).andExpect(status().isNotFound());
        jdbc.update("UPDATE users SET data_generation=1 WHERE id=900");poll(900,id).andExpect(status().isNotFound());
        jdbc.update("UPDATE users SET data_generation=0 WHERE id=900");
        jdbc.update("UPDATE ai_jobs SET expires_at=CURRENT_TIMESTAMP-INTERVAL '1 second' WHERE id=?",id);
        poll(900,id).andExpect(status().isNotFound());jobs.purgeExpiredBodies();
        assertThat(jobs.find(id).orElseThrow().result()).isNull();
    }
    @Test void oversizedOutputFailsWithoutSavingDiary()throws Exception{
        client.answer=r->new DiaryDraftResult("가".repeat(5001));var id=id(start("bad"));executor.run(id);
        poll(900,id).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FAILED")).andExpect(jsonPath("$.failure.reason").value("INVALID_OUTPUT"));
        assertThat(jdbc.queryForObject("SELECT final_diary FROM after_logs WHERE id=900",String.class)).isNull();
    }
    @Test void editDuringGenerationInvalidatesLateResult()throws Exception{
        client.answer=r->{
            try{mvc.perform(patch("/api/after-logs/900").header("X-API-Version","1").contentType("application/json").content("{\"inputVersion\":1,\"freeWriting\":\"수정 원문\"}" )).andExpect(status().isOk());}
            catch(Exception e){throw new RuntimeException(e);}return new DiaryDraftResult("이전 원문 초안");
        };
        var id=id(start("edit"));executor.run(id);
        assertThat(jobs.find(id).orElseThrow().failureReason()).isEqualTo("STALE_INPUT");
        assertThat(jobs.find(id).orElseThrow().result()).isNull();
    }
    @Test void requiredKeyAndIntegerVersionAndUnknownFieldsAreChecked()throws Exception{
        request(900,null,"{\"inputVersion\":1}").andExpect(status().isBadRequest());
        for(var body:new String[]{"{}","{\"inputVersion\":1.5}","{\"inputVersion\":1,\"content\":\"client text\"}"})request(900,"invalid",body).andExpect(status().isBadRequest());
    }
}
