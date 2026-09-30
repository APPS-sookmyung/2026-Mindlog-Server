package com.apps.mindlog.after;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Transactional
@WithMockUser(username="900") @Import(AfterTestAdapters.class)
class LatestAnalysisApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @BeforeEach void fixture(){
        for(int id=900;id<=901;id++){
            jdbc.update("""
                INSERT INTO users(id,email,nickname,representative_character_id,account_status,onboarding_completed,
                    after_reminder_enabled,weekly_report_start_day,created_at,updated_at)
                VALUES(?,?,'예선',1,'active',true,true,'sun',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """,id,id+"@latest.example");
            jdbc.update("INSERT INTO before_logs(id,user_id,situation_type_id,scheduled_at,expected_score,worst_scenario,created_at,updated_at) VALUES(?,?,1,'2026-09-30',80,'걱정',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",id,id);
            jdbc.update("INSERT INTO after_logs(id,before_log_id,free_writing,record_status,created_at,updated_at) VALUES(?,?,'서버 원문','DRAFT',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",id,id);
        }
    }
    void feedback(String status,long version){jdbc.update("INSERT INTO af_ai_feedbacks(after_log_id,analysis_status,input_version,summary,completed_at,created_at,updated_at) VALUES(900,?,?,'요약','2026-09-30T00:00:00Z',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",status,version);}
    org.springframework.test.web.servlet.ResultActions read(long id)throws Exception{return mvc.perform(get("/api/after-logs/"+id+"/analysis").header("X-API-Version","1"));}
    @Test void noHistoryIsNotRequestedAndNonCompletedStatesHideResults()throws Exception{
        read(900).andExpect(status().isOk()).andExpect(jsonPath("$.analysisStatus").value("NOT_REQUESTED"));
        feedback("PENDING",1);
        for(var state:new String[]{"PENDING","PROCESSING","FAILED","INVALIDATED"}){
            jdbc.update("UPDATE af_ai_feedbacks SET analysis_status=? WHERE after_log_id=900",state);em.clear();
            read(900).andExpect(status().isOk()).andExpect(jsonPath("$.analysisStatus").value(state))
                    .andExpect(jsonPath("$.result").isEmpty()).andExpect(jsonPath("$.completedAt").isEmpty());
        }
    }
    @Test void completedEvidenceAndCardsHaveCanonicalHistoricalNamesAndPatternCount()throws Exception{
        feedback("COMPLETED",1);
        jdbc.update("INSERT INTO distortion_tags(id,name,definition,example_thoughts,self_questions,reframes,display_order,active,created_at,updated_at) VALUES(900,'마음 읽기','테스트','[]','[]','[]',1,false,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO after_distortions(after_log_id,distortion_tag_id,evidence,explanation,created_at) VALUES(900,900,'서버','해석1',CURRENT_TIMESTAMP),(900,900,'원문','해석2',CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO after_analysis_cards(after_log_id,input_version,distortion_tag_id,tag_label,title,content,display_order,created_at) VALUES(900,1,900,'마음 읽기','두번째','설명',2,CURRENT_TIMESTAMP),(900,1,NULL,'돌아보기','첫번째','설명',1,CURRENT_TIMESTAMP)");
        read(900).andExpect(status().isOk()).andExpect(jsonPath("$.result.patternCount").value(2))
                .andExpect(jsonPath("$.result.distortions[0].distortionTagName").value("마음 읽기"))
                .andExpect(jsonPath("$.result.analysisCards[0].title").value("첫번째"))
                .andExpect(jsonPath("$.completedAt").value("2026-09-30T09:00:00+09:00"))
                .andExpect(jsonPath("$.result.externalFactors").doesNotExist());
    }
    @Test void zeroPatternsCanHaveGeneralReflectionCard()throws Exception{
        feedback("COMPLETED",1);
        jdbc.update("INSERT INTO after_analysis_cards(after_log_id,input_version,tag_label,title,content,display_order,created_at) VALUES(900,1,'돌아보기','일반 카드','설명',1,CURRENT_TIMESTAMP)");
        read(900).andExpect(status().isOk()).andExpect(jsonPath("$.result.patternCount").value(0))
                .andExpect(jsonPath("$.result.distortions").isEmpty()).andExpect(jsonPath("$.result.analysisCards[0].distortionTagId").isEmpty());
    }
    @Test void oldVersionFeedbackIsInvalidatedWithoutResultOrCompletionTime()throws Exception{
        feedback("COMPLETED",1);jdbc.update("UPDATE after_logs SET input_version=2 WHERE id=900");
        read(900).andExpect(status().isOk()).andExpect(jsonPath("$.analysisStatus").value("INVALIDATED"))
                .andExpect(jsonPath("$.inputVersion").value(2)).andExpect(jsonPath("$.result").isEmpty()).andExpect(jsonPath("$.completedAt").isEmpty());
    }
    @Test void cardsFromDifferentVersionAreNeverShown()throws Exception{
        feedback("COMPLETED",1);
        jdbc.update("INSERT INTO after_analysis_cards(after_log_id,input_version,tag_label,title,content,display_order,created_at) VALUES(900,2,'돌아보기','다른 버전','설명',1,CURRENT_TIMESTAMP)");
        read(900).andExpect(status().isOk()).andExpect(jsonPath("$.result.analysisCards").isEmpty());
    }
    @Test void missingAndForeignRecordsReturn404()throws Exception{
        for(long id:new long[]{901,999})read(id).andExpect(status().isNotFound());
    }
}
