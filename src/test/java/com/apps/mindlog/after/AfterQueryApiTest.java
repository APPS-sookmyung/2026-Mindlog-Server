package com.apps.mindlog.after;

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
class AfterQueryApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @BeforeEach void fixtures(){
        for(int id=900;id<=901;id++)jdbc.update("""
            INSERT INTO users(id,email,nickname,representative_character_id,account_status,onboarding_completed,
                after_reminder_enabled,weekly_report_start_day,created_at,updated_at)
            VALUES(?,?,'예선',1,'active',true,true,'sun',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """,id,id+"@query.example");
        for(int id=900;id<=902;id++)jdbc.update("""
            INSERT INTO before_logs(id,user_id,situation_type_id,scheduled_at,expected_score,worst_scenario,title,description,created_at,updated_at)
            VALUES(?,?,?,'2026-10-01',80,'걱정','발표 제목','설명',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """,id,id==902?901:900,id==901?3:1);
        jdbc.update("INSERT INTO after_logs(id,before_log_id,free_writing,record_status,created_at,updated_at) VALUES(900,900,'원문','DRAFT','2026-09-29T15:00:00Z','2026-09-29T15:00:00Z')");
        jdbc.update("INSERT INTO after_logs(id,before_log_id,free_writing,record_status,actual_score,final_diary,finalized_at,created_at,updated_at) VALUES(901,901,'둘째 원문','FINALIZED',52,'최종 일기','2026-09-29T14:00:00Z','2026-09-29T14:00:00Z','2026-09-29T14:00:00Z')");
        jdbc.update("INSERT INTO after_logs(id,before_log_id,free_writing,record_status,created_at,updated_at) VALUES(902,902,'타인 원문','DRAFT',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO after_symptoms(after_log_id,body_symptom_id,created_at) VALUES(900,1,CURRENT_TIMESTAMP)");
    }
    org.springframework.test.web.servlet.ResultActions read(String path)throws Exception{return mvc.perform(get(path).header("X-API-Version","1"));}
    @Test void listIncludesOnlyOwnerDraftAndFinalizedWithPageContract()throws Exception{
        read("/api/after-logs").andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.page.size").value(20)).andExpect(jsonPath("$.content[0].id").value(900))
                .andExpect(jsonPath("$.content[0].actualScore").isEmpty()).andExpect(jsonPath("$.content[0].hasFinalDiary").value(false))
                .andExpect(jsonPath("$.content[1].actualScore").value(52)).andExpect(jsonPath("$.content[1].hasFinalDiary").value(true));
    }
    @Test void dateFilterUsesInclusiveSeoulCreationDates()throws Exception{
        read("/api/after-logs?from=2026-09-30&to=2026-09-30").andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1)).andExpect(jsonPath("$.content[0].id").value(900));
        read("/api/after-logs?to=2026-09-29").andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(901));
    }
    @Test void situationFilterAllowsHistoricalInactiveType()throws Exception{
        jdbc.update("UPDATE situation_types SET active=false WHERE id=3");
        read("/api/after-logs?situationTypeId=3").andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1)).andExpect(jsonPath("$.content[0].id").value(901));
    }
    @Test void paginationClampsSizeAndUsesStableTieBreak()throws Exception{
        jdbc.update("UPDATE after_logs SET created_at='2026-09-29T15:00:00Z' WHERE id=901");
        read("/api/after-logs?size=1&page=1").andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(900));
        read("/api/after-logs?size=101").andExpect(status().isOk()).andExpect(jsonPath("$.page.size").value(100));
    }
    @Test void invalidQueryIs400AndEmptyPageIs200()throws Exception{
        for(String query:new String[]{"page=-1","size=0","from=2026-10-02&to=2026-10-01","from=bad","situationTypeId=999"})
            read("/api/after-logs?"+query).andExpect(status().isBadRequest());
        read("/api/after-logs?page=5").andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
    }
    @Test void detailReturnsOriginalAndReadonlyBeforeWithHistoricalSymptoms()throws Exception{
        jdbc.update("UPDATE body_symptoms SET active=false WHERE id=1");
        read("/api/after-logs/900").andExpect(status().isOk()).andExpect(jsonPath("$.freeWriting").value("원문"))
                .andExpect(jsonPath("$.title").value("발표 제목")).andExpect(jsonPath("$.description").value("설명"))
                .andExpect(jsonPath("$.scheduledAt").value("2026-10-01")).andExpect(jsonPath("$.worstScenario").value("걱정"))
                .andExpect(jsonPath("$.bodySymptoms[0].id").value(1)).andExpect(jsonPath("$.scoreDiff").isEmpty())
                .andExpect(jsonPath("$.finalDiary").isEmpty()).andExpect(jsonPath("$.inputVersion").value(1));
        read("/api/after-logs/901").andExpect(status().isOk()).andExpect(jsonPath("$.scoreDiff").value(-28))
                .andExpect(jsonPath("$.finalDiary").value("최종 일기"));
    }
    @Test void foreignAndMissingDetailAre404()throws Exception{
        for(long id:new long[]{902,999})read("/api/after-logs/"+id).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://api.mindlog.com/problems/resource-not-found"));
    }
    @Test void staleFeedbackIsReportedInvalidated()throws Exception{
        jdbc.update("UPDATE after_logs SET input_version=2 WHERE id=900");
        jdbc.update("INSERT INTO af_ai_feedbacks(after_log_id,analysis_status,input_version,summary,created_at,updated_at) VALUES(900,'COMPLETED',1,'오래된 요약',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        read("/api/after-logs/900").andExpect(status().isOk()).andExpect(jsonPath("$.analysisStatus").value("INVALIDATED"));
        read("/api/after-logs").andExpect(status().isOk()).andExpect(jsonPath("$.content[0].analysisStatus").value("INVALIDATED"));
    }
}
