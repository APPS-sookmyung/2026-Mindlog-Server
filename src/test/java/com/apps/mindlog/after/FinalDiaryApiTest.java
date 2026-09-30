package com.apps.mindlog.after;

import com.apps.mindlog.ai.job.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @WithMockUser(username="900") @Import(AfterTestAdapters.class)
class FinalDiaryApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean JobStore jobs;
    static final String BODY="{\"inputVersion\":1,\"finalDiary\":\"최종 일기\",\"actualScore\":52}";
    @BeforeEach void fixture(){
        cleanup();
        for(int id=900;id<=901;id++){
            jdbc.update("""
                INSERT INTO users(id,email,nickname,representative_character_id,account_status,onboarding_completed,
                    after_reminder_enabled,weekly_report_start_day,created_at,updated_at)
                VALUES(?,?,'예선',1,'active',true,true,'sun',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """,id,id+"@final.example");
            jdbc.update("""
                INSERT INTO before_logs(id,user_id,situation_type_id,scheduled_at,expected_score,expected_score_comment,worst_scenario,created_at,updated_at)
                VALUES(?,?,1,'2026-09-30',80,'발표가 걱정돼요','걱정',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """,id,id);
            jdbc.update("INSERT INTO after_logs(id,before_log_id,free_writing,record_status,created_at,updated_at) VALUES(?,?,'원문','DRAFT',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",id,id);
        }
        jdbc.update("INSERT INTO af_ai_feedbacks(after_log_id,analysis_status,input_version,summary,completed_at,created_at,updated_at) VALUES(900,'COMPLETED',1,'요약',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("""
            INSERT INTO ai_jobs(id,user_id,after_log_id,kind,status,request_key,input_version,data_generation,
                input_snapshot,result,retryable,expires_at,completed_at,created_at,updated_at)
            VALUES(?,900,900,'diary_draft','COMPLETED',?,1,0,'{}',NULL,false,
                CURRENT_TIMESTAMP-INTERVAL '1 hour',CURRENT_TIMESTAMP-INTERVAL '90 minutes',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """,UUID.randomUUID(),UUID.randomUUID());
        jdbc.update("""
            INSERT INTO notifications(id,user_id,before_log_id,type,priority,dedup_key,status,scheduled_at,created_at,updated_at)
            VALUES(900,900,900,'after_induce',1,'after:900','pending',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),
                  (901,901,901,'after_induce',1,'after:901','pending',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),
                  (902,900,900,'after_induce',1,'dismissed:900','dismissed',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """);
    }
    @AfterEach void cleanup(){jdbc.update("DELETE FROM users WHERE id IN (900,901)");}
    org.springframework.test.web.servlet.ResultActions save(long id,String key,String body)throws Exception{
        var request=put("/api/after-logs/"+id+"/final-diary").header("X-API-Version","1").contentType("application/json").content(body);
        if(key!=null)request.header("Idempotency-Key",key);return mvc.perform(request);
    }
    @Test void expiredDraftMetadataStillAllowsAtomicFinalization()throws Exception{
        save(900,"save",BODY).andExpect(status().isOk()).andExpect(jsonPath("$.recordStatus").value("FINALIZED"))
                .andExpect(jsonPath("$.inputVersion").value(1)).andExpect(jsonPath("$.scoreDiff").value(-28))
                .andExpect(jsonPath("$.comparison.direction").value("DOWN")).andExpect(jsonPath("$.comparison.changeAmount").value(28))
                .andExpect(jsonPath("$.comparison.beforeComment").value("발표가 걱정돼요"))
                .andExpect(jsonPath("$.insightStatus").value("PENDING"));
        assertThat(jdbc.queryForObject("SELECT status FROM notifications WHERE id=900",String.class)).isEqualTo("cancelled");
        assertThat(jdbc.queryForObject("SELECT status FROM notifications WHERE id=901",String.class)).isEqualTo("pending");
        assertThat(jdbc.queryForObject("SELECT status FROM notifications WHERE id=902",String.class)).isEqualTo("dismissed");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_jobs WHERE kind='record_insight'",Integer.class)).isEqualTo(1);
    }
    @Test void sameKeyReturnsExactResponseWithoutAnotherInsight()throws Exception{
        String first=save(900,"same",BODY).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String second=save(900,"same",BODY).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(second).isEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_jobs WHERE kind='record_insight'",Integer.class)).isEqualTo(1);
    }
    @Test void changedInputSameKeyAndNewKeyRefinalizationAreDifferentConflicts()throws Exception{
        save(900,"key",BODY).andExpect(status().isOk());
        save(900,"key",BODY.replace("52","53")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://api.mindlog.com/problems/idempotency-conflict"));
        save(900,"different",BODY).andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://api.mindlog.com/problems/final-diary-already-confirmed"));
    }
    @Test void incompleteAnalysisOrDifferentGenerationKeepsDraft()throws Exception{
        jdbc.update("UPDATE af_ai_feedbacks SET analysis_status='INVALIDATED' WHERE after_log_id=900");
        save(900,"not-ready",BODY).andExpect(status().isConflict()).andExpect(jsonPath("$.type").value("https://api.mindlog.com/problems/analysis-not-ready"));
        jdbc.update("UPDATE af_ai_feedbacks SET analysis_status='COMPLETED' WHERE after_log_id=900");
        jdbc.update("UPDATE users SET data_generation=1 WHERE id=900");
        save(900,"generation",BODY).andExpect(status().isConflict());assertDraft();
    }
    @Test void staleVersionAndMissingDraftCompletionAreRejected()throws Exception{
        save(900,"stale",BODY.replace("\"inputVersion\":1","\"inputVersion\":2")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://api.mindlog.com/problems/stale-input-version"));
        jdbc.update("DELETE FROM ai_jobs WHERE kind='diary_draft'");
        save(900,"no-draft",BODY).andExpect(status().isConflict()).andExpect(jsonPath("$.type").value("https://api.mindlog.com/problems/analysis-not-ready"));
        assertDraft();
    }
    @Test void insightRegistrationFailureRollsBackRecordNotificationAndIdempotency()throws Exception{
        doThrow(new IllegalStateException("test failure")).when(jobs).submit(eq(900L),eq(0L),isNull(),eq(900L),eq(JobKind.RECORD_INSIGHT),eq(1L),any(UUID.class),anyString());
        save(900,"rollback",BODY).andExpect(status().isInternalServerError());assertDraft();
        assertThat(jdbc.queryForObject("SELECT status FROM notifications WHERE id=900",String.class)).isEqualTo("pending");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM idempotency_records WHERE user_id=900",Integer.class)).isZero();
    }
    @Test void missingKeyInvalidScoreAndTooLongDiaryAre400()throws Exception{
        save(900,null,BODY).andExpect(status().isBadRequest());
        for(String score:new String[]{"-1","101","1.5","\"52\"","null"})
            save(900,"bad",BODY.replace("52",score)).andExpect(status().isBadRequest());
        save(900,"long",BODY.replace("최종 일기","가".repeat(5001))).andExpect(status().isBadRequest());
        assertDraft();
    }
    @Test void foreignAndMissingPathAre404BeforeCompletionDetails()throws Exception{
        for(long id:new long[]{901,999})save(id,"owned",BODY).andExpect(status().isNotFound());
        assertDraft();
    }
    @Test void actualZeroIsValid()throws Exception{
        save(900,"zero",BODY.replace("52","0")).andExpect(status().isOk())
                .andExpect(jsonPath("$.actualScore").value(0)).andExpect(jsonPath("$.scoreDiff").value(-80));
    }
    void assertDraft(){
        assertThat(jdbc.queryForObject("SELECT record_status FROM after_logs WHERE id=900",String.class)).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("SELECT actual_score FROM after_logs WHERE id=900",Integer.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT final_diary FROM after_logs WHERE id=900",String.class)).isNull();
    }
}
