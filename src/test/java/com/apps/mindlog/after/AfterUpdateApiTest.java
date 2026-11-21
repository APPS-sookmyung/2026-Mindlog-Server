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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Transactional
@WithMockUser(username="900") @Import(AfterTestAdapters.class)
class AfterUpdateApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JobStore jobs;
    @BeforeEach void fixtures(){
        for(int id=900;id<=901;id++){
            jdbc.update("""
                INSERT INTO users(id,email,nickname,representative_character_id,account_status,onboarding_completed,
                    after_reminder_enabled,weekly_report_start_day,created_at,updated_at)
                VALUES(?,?,'예선',1,'active',true,true,'sun',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """,id,id+"@update.example");
            jdbc.update("""
                INSERT INTO before_logs(id,user_id,situation_type_id,scheduled_at,expected_score,worst_scenario,created_at,updated_at)
                VALUES(?,?,1,'2026-09-30',80,'걱정',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """,id,id);
            jdbc.update("INSERT INTO after_logs(id,before_log_id,free_writing,record_status,created_at,updated_at) VALUES(?,?,'원문','DRAFT',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",id,id);
        }
        jdbc.update("INSERT INTO after_symptoms(after_log_id,body_symptom_id,created_at) VALUES(900,1,CURRENT_TIMESTAMP),(900,4,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO af_ai_feedbacks(after_log_id,analysis_status,input_version,summary,input_snapshot,completed_at,created_at,updated_at) VALUES(900,'COMPLETED',1,'이전 요약','{}',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO distortion_tags(id,name,definition,example_thoughts,self_questions,reframes,display_order,active,created_at,updated_at) VALUES(900,'테스트','테스트','[]','[]','[]',1,true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO after_distortions(after_log_id,distortion_tag_id,evidence,explanation,created_at) VALUES(900,900,'원문','해석',CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO after_analysis_cards(after_log_id,input_version,tag_label,title,content,display_order,created_at) VALUES(900,1,'돌아보기','제목','설명',1,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO ai_insights(after_log_id,insight_type,display_order,insight_content,created_at,updated_at) VALUES(900,'reflection_question',1,'질문',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
    }
    org.springframework.test.web.servlet.ResultActions update(long id,String body)throws Exception{
        return mvc.perform(patch("/api/after-logs/"+id).header("X-API-Version","1").contentType("application/json").content(body));
    }
    @Test void actualChangeIncrementsOnceAndRemovesAllPriorAnalysis()throws Exception{
        update(900,"{\"inputVersion\":1,\"freeWriting\":\"수정 원문\",\"bodySymptomIds\":[1,5]}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.inputVersion").value(2))
                .andExpect(jsonPath("$.analysisStatus").value("INVALIDATED")).andExpect(jsonPath("$.freeWriting").value("수정 원문"));
        for(var table:new String[]{"after_distortions","after_analysis_cards","ai_insights"})
            assertThat(jdbc.queryForObject("SELECT count(*) FROM "+table+" WHERE after_log_id=900",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT summary FROM af_ai_feedbacks WHERE after_log_id=900",String.class)).isNull();
        assertThat(jdbc.queryForList("SELECT body_symptom_id FROM after_symptoms WHERE after_log_id=900 ORDER BY body_symptom_id",Long.class)).containsExactly(1L,5L);
    }
    @Test void identicalTextAndReorderedSymptomsPreserveVersionAndAnalysis()throws Exception{
        update(900,"{\"inputVersion\":1,\"freeWriting\":\" 원문 \",\"bodySymptomIds\":[4,1]}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.inputVersion").value(1))
                .andExpect(jsonPath("$.analysisStatus").value("COMPLETED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM after_analysis_cards WHERE after_log_id=900",Integer.class)).isEqualTo(1);
    }
    @Test void partialPatchKeepsUnspecifiedSymptoms()throws Exception{
        update(900,"{\"inputVersion\":1,\"freeWriting\":\"다른 원문\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.bodySymptoms.length()").value(2));
        update(900,"{\"inputVersion\":2}").andExpect(status().isOk()).andExpect(jsonPath("$.inputVersion").value(2));
    }
    @Test void invalidSelectionsAndExplicitNullOrForbiddenFieldsDoNotChangeInput()throws Exception{
        for(var field:new String[]{"\"freeWriting\":null","\"bodySymptomIds\":null","\"bodySymptomIds\":[]",
                "\"bodySymptomIds\":[8]","\"bodySymptomIds\":[1,1]","\"situationTypeId\":1","\"finalDiary\":\"확정\""})
            update(900,"{\"inputVersion\":1,"+field+"}").andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT input_version FROM after_logs WHERE id=900",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT free_writing FROM after_logs WHERE id=900",String.class)).isEqualTo("원문");
    }
    @Test void staleInputIsRejected()throws Exception{
        update(900,"{\"inputVersion\":2,\"freeWriting\":\"수정\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://api.mindlog.com/problems/stale-input-version"));
    }
    @Test void finalizedRecordIsRejected()throws Exception{
        jdbc.update("UPDATE after_logs SET record_status='FINALIZED',actual_score=30,final_diary='일기',finalized_at=CURRENT_TIMESTAMP WHERE id=900");
        update(900,"{\"inputVersion\":1,\"freeWriting\":\"수정\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://api.mindlog.com/problems/after-log-finalized"));
    }
    @Test void foreignAndMissingRecordUseSame404()throws Exception{
        for(long id:new long[]{901,999})update(id,"{\"inputVersion\":1,\"freeWriting\":\"수정\"}").andExpect(status().isNotFound());
    }
    @Test void activeAnalysisBlocksMutation()throws Exception{
        jobs.submit(900,0,null,900L,JobKind.AFTER_ANALYSIS,1,UUID.randomUUID(),"{}");
        update(900,"{\"inputVersion\":1,\"freeWriting\":\"수정\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://api.mindlog.com/problems/analysis-in-progress"));
        assertThat(jdbc.queryForObject("SELECT input_version FROM after_logs WHERE id=900",Long.class)).isEqualTo(1);
    }
    @Test void runningDiaryResultCannotApplyAfterEdit()throws Exception{
        UUID id=jobs.submit(900,0,null,900L,JobKind.DIARY_DRAFT,1,UUID.randomUUID(),"{\"original\":\"원문\"}");
        var attempt=jobs.claim(id).orElseThrow();
        update(900,"{\"inputVersion\":1,\"freeWriting\":\"수정\"}").andExpect(status().isOk());
        assertThat(jobs.complete(attempt,"{\"content\":\"늦은 초안\"}",job->true,()->fail("must not apply"))).isFalse();
        var invalidated=jobs.find(id).orElseThrow();assertThat(invalidated.status()).isEqualTo(JobStatus.FAILED);
        assertThat(invalidated.failureReason()).isEqualTo("STALE_INPUT");assertThat(invalidated.inputSnapshot()).isEqualTo("{}");
    }
}
