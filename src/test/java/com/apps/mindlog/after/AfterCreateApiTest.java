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
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Transactional
@WithMockUser(username="900") @Import(AfterTestAdapters.class)
class AfterCreateApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @BeforeEach void fixtures(){
        for(int id=900;id<=901;id++)jdbc.update("""
            INSERT INTO users(id,email,nickname,representative_character_id,account_status,onboarding_completed,
                after_reminder_enabled,weekly_report_start_day,created_at,updated_at)
            VALUES(?,?,'예선',1,'active',true,true,'sun',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """,id,id+"@after.example");
        for(int id=900;id<=901;id++)jdbc.update("""
            INSERT INTO before_logs(id,user_id,situation_type_id,scheduled_at,expected_score,worst_scenario,created_at,updated_at)
            VALUES(?,?,1,'2026-09-30',80,'걱정',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """,id,id);
    }
    org.springframework.test.web.servlet.ResultActions create(String body)throws Exception{
        return mvc.perform(post("/api/after-logs").header("X-API-Version","1").contentType("application/json").content(body));
    }
    @Test void savesOnlyDraftAndSymptomsWithoutCallingAi()throws Exception{
        create("{\"beforeLogId\":900,\"freeWriting\":\" 원문 \",\"bodySymptomIds\":[4,1]}")
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.recordStatus").value("DRAFT"))
                .andExpect(jsonPath("$.inputVersion").value(1)).andExpect(jsonPath("$.actualScore").isEmpty())
                .andExpect(jsonPath("$.finalizedAt").isEmpty()).andExpect(jsonPath("$.analysisStatus").value("NOT_REQUESTED"))
                .andExpect(jsonPath("$.situationTypeId").value(1)).andExpect(jsonPath("$.situationTypeName").value("발표"))
                .andExpect(jsonPath("$.bodySymptoms[0].id").value(1)).andExpect(jsonPath("$.freeWriting").value("원문"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM after_symptoms",Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT final_diary FROM after_logs",String.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_jobs",Integer.class)).isZero();
    }
    @Test void existingOwnedAfterReturnsItsIdWith409()throws Exception{
        String body="{\"beforeLogId\":900,\"freeWriting\":\"원문\",\"bodySymptomIds\":[1]}";
        create(body).andExpect(status().isCreated());
        Long id=jdbc.queryForObject("SELECT id FROM after_logs",Long.class);
        create(body).andExpect(status().isConflict()).andExpect(jsonPath("$.afterLogId").value(id))
                .andExpect(jsonPath("$.type").value("https://api.mindlog.com/problems/after-log-already-exists"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM after_logs",Integer.class)).isEqualTo(1);
    }
    @Test void foreignBeforeIsHiddenBeforeDuplicateLookup()throws Exception{
        jdbc.update("INSERT INTO after_logs(before_log_id,free_writing,record_status,created_at,updated_at) VALUES(901,'비공개','DRAFT',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        for(long id:new long[]{901,999})create("{\"beforeLogId\":"+id+",\"freeWriting\":\"원문\",\"bodySymptomIds\":[1]}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.afterLogId").doesNotExist())
                .andExpect(jsonPath("$.type").value("https://api.mindlog.com/problems/validation-error"));
    }
    @Test void invalidSymptomsLeaveNoDraft()throws Exception{
        for(String ids:new String[]{"[]","[8]","[1,1]","[999]","[null]"})
            create("{\"beforeLogId\":900,\"freeWriting\":\"원문\",\"bodySymptomIds\":"+ids+"}").andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM after_logs",Integer.class)).isZero();
    }
    @Test void forbiddenSituationAndFinalFieldsAreRejected()throws Exception{
        for(String extra:new String[]{"\"situationTypeId\":1","\"actualScore\":20","\"finalDiary\":\"미리 확정\""})
            create("{\"beforeLogId\":900,\"freeWriting\":\"원문\",\"bodySymptomIds\":[1],"+extra+"}").andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM after_logs",Integer.class)).isZero();
    }
    @Test void emptyAndTooLongOriginalAreRejected()throws Exception{
        for(String text:new String[]{" ","가".repeat(301)})
            create("{\"beforeLogId\":900,\"freeWriting\":\""+text+"\",\"bodySymptomIds\":[1]}").andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM after_logs",Integer.class)).isZero();
    }
    @Test void incompleteOnboardingCannotCreate()throws Exception{
        jdbc.update("UPDATE users SET onboarding_completed=false WHERE id=900");
        create("{\"beforeLogId\":900,\"freeWriting\":\"원문\",\"bodySymptomIds\":[1]}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://api.mindlog.com/problems/onboarding-required"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM after_logs",Integer.class)).isZero();
    }
}
