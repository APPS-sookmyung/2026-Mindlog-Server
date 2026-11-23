package com.apps.mindlog.after;

import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="mindlog.ai.max-requests-per-hour=2") @AutoConfigureMockMvc @Transactional
@WithMockUser(username="900") @Import(AfterTestAdapters.class)
class AfterAnalysisApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;
    @BeforeEach void fixture(){
        for(int id=900;id<=901;id++)jdbc.update("""
            INSERT INTO users(id,email,nickname,representative_character_id,account_status,onboarding_completed,
                after_reminder_enabled,weekly_report_start_day,created_at,updated_at)
            VALUES(?,?,'예선',1,'active',true,true,'sun',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """,id,id+"@analysis.example");
        for(int id=900;id<=903;id++){
            jdbc.update("INSERT INTO before_logs(id,user_id,situation_type_id,scheduled_at,expected_score,worst_scenario,created_at,updated_at) VALUES(?,?,1,'2026-09-30',80,'걱정',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",id,id==901?901:900);
            jdbc.update("INSERT INTO after_logs(id,before_log_id,free_writing,record_status,created_at,updated_at) VALUES(?,?,'서버 원문','DRAFT',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",id,id);
        }
        jdbc.update("INSERT INTO after_symptoms(after_log_id,body_symptom_id,created_at) VALUES(900,1,CURRENT_TIMESTAMP)");
    }
    org.springframework.test.web.servlet.ResultActions request(long id,String key,String body)throws Exception{
        var req=post("/api/after-logs/"+id+"/analyses").header("X-API-Version","1").contentType("application/json").content(body);
        if(key!=null)req.header("Idempotency-Key",key);return mvc.perform(req);
    }
    org.springframework.test.web.servlet.ResultActions poll(long id,String job)throws Exception{
        return mvc.perform(get("/api/after-logs/"+id+"/analyses/"+job).header("X-API-Version","1"));
    }
    String start(long id,String key)throws Exception{
        return request(id,key,"{\"inputVersion\":1}").andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
    }
    @Test void registersServerSnapshotWith202AndPollingContract()throws Exception{
        var response=request(900,"key","{\"inputVersion\":1}").andExpect(status().isAccepted())
                .andExpect(header().string("Retry-After","2")).andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.inputVersion").value(1)).andReturn().getResponse().getContentAsString();
        String id=json.readTree(response).get("jobId").asText();
        poll(900,id).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.result").isEmpty()).andExpect(jsonPath("$.failure").isEmpty())
                .andExpect(jsonPath("$.inputSnapshot").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT input_snapshot::text FROM ai_jobs WHERE id=?",String.class,UUID.fromString(id))).contains("서버 원문","bodySymptomIds");
    }
    @Test void replayDoesNotCreateJobOrConsumeQuotaTwice()throws Exception{
        String first=start(900,"same");String second=start(900,"same");assertThat(second).isEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_jobs",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT used FROM llm_quota_usage WHERE user_id=900",Integer.class)).isEqualTo(1);
        request(900,"same","{\"inputVersion\":2}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://api.mindlog.com/problems/idempotency-conflict"));
    }
    @Test void activeJobBlocksDifferentKeyButExistingFeedbackDoesNot()throws Exception{
        jdbc.update("INSERT INTO af_ai_feedbacks(after_log_id,analysis_status,input_version,summary,created_at,updated_at) VALUES(900,'COMPLETED',1,'이전 요약',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        start(900,"first");
        request(900,"different","{\"inputVersion\":1}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://api.mindlog.com/problems/analysis-in-progress"));
    }
    @Test void quotaExceededDoesNotLeaveJobOrPendingFeedback()throws Exception{
        start(900,"one");start(902,"two");
        request(903,"three","{\"inputVersion\":1}").andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After")).andExpect(jsonPath("$.type").value("https://api.mindlog.com/problems/rate-limited"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_jobs WHERE after_log_id=903",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM af_ai_feedbacks WHERE after_log_id=903",Integer.class)).isZero();
    }
    @Test void failedPollingIs200WithSafeFailure()throws Exception{
        String id=json.readTree(start(900,"failed")).get("jobId").asText();
        jdbc.update("UPDATE ai_jobs SET status='FAILED',failure_reason='PROVIDER_UNAVAILABLE',retryable=true,input_snapshot='{}' WHERE id=?",UUID.fromString(id));
        poll(900,id).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.result").isEmpty()).andExpect(jsonPath("$.failure.reason").value("PROVIDER_UNAVAILABLE"))
                .andExpect(jsonPath("$.failure.retryable").value(true));
    }
    @Test void wrongTargetForeignRecordMissingJobAndOldGenerationAre404()throws Exception{
        String id=json.readTree(start(900,"poll")).get("jobId").asText();
        poll(902,id).andExpect(status().isNotFound());poll(901,id).andExpect(status().isNotFound());
        poll(900,UUID.randomUUID().toString()).andExpect(status().isNotFound());
        jdbc.update("UPDATE users SET data_generation=1 WHERE id=900");poll(900,id).andExpect(status().isNotFound());
    }
    @Test void staleInputIsRejectedBeforeQuota()throws Exception{
        request(900,"stale","{\"inputVersion\":2}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://api.mindlog.com/problems/stale-input-version"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM llm_quota_usage",Integer.class)).isZero();
    }
    @Test void keyVersionAndServerOnlyInputAreValidated()throws Exception{
        request(900,null,"{\"inputVersion\":1}").andExpect(status().isBadRequest());
        for(var body:new String[]{"{}","{\"inputVersion\":1.5}","{\"inputVersion\":1,\"freeWriting\":\"변조\"}"})
            request(900,"invalid",body).andExpect(status().isBadRequest());
        request(901,"foreign","{\"inputVersion\":1}").andExpect(status().isNotFound());
    }
}
