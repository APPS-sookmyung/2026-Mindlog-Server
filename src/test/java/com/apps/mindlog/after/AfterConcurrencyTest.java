package com.apps.mindlog.after;

import com.apps.mindlog.ai.job.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest(properties={"mindlog.ai.max-requests-per-hour=20","mindlog.ai.dispatch-enabled=false"})
@AutoConfigureMockMvc @Import(AfterTestAdapters.class)
class AfterConcurrencyTest {
    @Autowired JdbcTemplate jdbc; @Autowired MockMvc mvc; @Autowired JsonMapper json; @Autowired JobStore jobs;
    static final String FINAL="{\"inputVersion\":1,\"finalDiary\":\"확정 일기\",\"actualScore\":0}";
    @BeforeEach void setup(){
        cleanup();
        jdbc.update("INSERT INTO users(id,email,nickname,representative_character_id,account_status,onboarding_completed,after_reminder_enabled,weekly_report_start_day,created_at,updated_at) VALUES(900,'race@example.com','예선',1,'active',true,true,'sun',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO before_logs(id,user_id,situation_type_id,scheduled_at,expected_score,worst_scenario,created_at,updated_at) VALUES(900,900,1,'2026-09-30',80,'걱정',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO after_logs(id,before_log_id,free_writing,record_status,created_at,updated_at) VALUES(900,900,'원문','DRAFT',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO after_symptoms(after_log_id,body_symptom_id,created_at) VALUES(900,1,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO af_ai_feedbacks(after_log_id,analysis_status,input_version,summary,completed_at,created_at,updated_at) VALUES(900,'COMPLETED',1,'분석',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO ai_jobs(id,user_id,after_log_id,kind,status,request_key,input_version,data_generation,input_snapshot,result,retryable,completed_at,created_at,updated_at) VALUES(?,900,900,'diary_draft','COMPLETED',?,1,0,'{}','{\"content\":\"초안\"}',false,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",UUID.randomUUID(),UUID.randomUUID());
    }
    @AfterEach void cleanup(){jdbc.update("DELETE FROM users WHERE id=900");}
    record Reply(int status,String body,String contentType){}
    Reply send(MockHttpServletRequestBuilder req)throws Exception{
        var response=mvc.perform(req.with(user("900")).header("X-API-Version","1").contentType("application/json")).andReturn().getResponse();
        return new Reply(response.getStatus(),response.getContentAsString(),response.getContentType());
    }
    <T>List<T> race(Callable<T> first,Callable<T> second)throws Exception{
        try(var pool=Executors.newFixedThreadPool(2)){
            var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
            List<Future<T>> futures=new ArrayList<>();
            for(var action:List.of(first,second))futures.add(pool.submit(()->{ready.countDown();if(!start.await(10,TimeUnit.SECONDS))throw new TimeoutException();return action.call();}));
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();start.countDown();
            List<T> result=new ArrayList<>();for(var future:futures)result.add(future.get(30,TimeUnit.SECONDS));return result;
        }
    }
    MockHttpServletRequestBuilder confirm(String key){return put("/api/after-logs/900/final-diary").header("Idempotency-Key",key).content(FINAL);}
    @Test void sameBeforeConcurrentCreateHasExactlyOneWinner()throws Exception{
        jdbc.update("DELETE FROM after_logs WHERE id=900");
        String body="{\"beforeLogId\":900,\"freeWriting\":\"원문\",\"bodySymptomIds\":[1]}";
        var result=race(()->send(post("/api/after-logs").content(body)),()->send(post("/api/after-logs").content(body)));
        assertThat(result).extracting(Reply::status).containsExactlyInAnyOrder(201,409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM after_logs WHERE before_log_id=900",Integer.class)).isEqualTo(1);
        var conflict=result.stream().filter(r->r.status()==409).findFirst().orElseThrow();
        assertThat(json.readTree(conflict.body()).get("afterLogId").longValue()).isEqualTo(jdbc.queryForObject("SELECT id FROM after_logs WHERE before_log_id=900",Long.class));
    }
    @Test void sameKeyFinalizationReplaysExactResponseAndRegistersOneInsight()throws Exception{
        var result=race(()->send(confirm("same")),()->send(confirm("same")));
        assertThat(result).extracting(Reply::status).containsExactly(200,200);assertThat(result.get(0)).isEqualTo(result.get(1));assertOneInsight();
    }
    @Test void differentKeysFinalizationOnlyOneSucceeds()throws Exception{
        var result=race(()->send(confirm("first")),()->send(confirm("second")));
        assertThat(result).extracting(Reply::status).containsExactlyInAnyOrder(200,409);assertOneInsight();
    }
    @Test void twoEditsOfSameVersionCannotOverwriteEachOther()throws Exception{
        var result=race(()->send(patch("/api/after-logs/900").content("{\"inputVersion\":1,\"freeWriting\":\"첫 수정\"}")),
                ()->send(patch("/api/after-logs/900").content("{\"inputVersion\":1,\"freeWriting\":\"둘째 수정\"}")));
        assertThat(result).extracting(Reply::status).containsExactlyInAnyOrder(200,409);
        assertThat(result.stream().filter(r->r.status()==409).findFirst().orElseThrow().body()).contains("stale-input-version");
        assertThat(jdbc.queryForObject("SELECT input_version FROM after_logs WHERE id=900",Long.class)).isEqualTo(2);
    }
    @Test void analysisRequestsSerializeBeforeQuotaAndJobInsertion()throws Exception{
        var result=race(()->send(post("/api/after-logs/900/analyses").header("Idempotency-Key","first").content("{\"inputVersion\":1}")),
                ()->send(post("/api/after-logs/900/analyses").header("Idempotency-Key","second").content("{\"inputVersion\":1}")));
        assertThat(result).extracting(Reply::status).containsExactlyInAnyOrder(202,409);
        assertThat(jdbc.queryForObject("SELECT used FROM llm_quota_usage WHERE user_id=900",Integer.class)).isEqualTo(1);
        UUID id=jdbc.queryForObject("SELECT id FROM ai_jobs WHERE kind='after_analysis' AND after_log_id=900",UUID.class);
        var attempts=race(()->jobs.claim(id),()->jobs.claim(id));assertThat(attempts.stream().filter(Optional::isPresent).count()).isEqualTo(1);
    }
    @Test void editAndFinalizationCannotCommitIncompatibleVersions()throws Exception{
        var result=race(()->send(confirm("final")),()->send(patch("/api/after-logs/900").content("{\"inputVersion\":1,\"freeWriting\":\"새 원문\"}")));
        assertThat(result).extracting(Reply::status).containsExactlyInAnyOrder(200,409);
        String state=jdbc.queryForObject("SELECT record_status FROM after_logs WHERE id=900",String.class);
        if("FINALIZED".equals(state)){assertOneInsight();assertThat(jdbc.queryForObject("SELECT input_version FROM after_logs WHERE id=900",Long.class)).isEqualTo(1);}
        else {assertThat(jdbc.queryForObject("SELECT input_version FROM after_logs WHERE id=900",Long.class)).isEqualTo(2);assertThat(jdbc.queryForObject("SELECT final_diary FROM after_logs WHERE id=900",String.class)).isNull();}
    }
    @Test void numericStringsDecimalsAndOversizedIntegersAreRejectedWithoutMutation()throws Exception{
        for(String value:List.of("\"900\"","900.9","9223372036854775808")){
            assertThat(send(post("/api/after-logs").content("{\"beforeLogId\":"+value+",\"freeWriting\":\"원문\",\"bodySymptomIds\":[1]}" )).status()).isEqualTo(400);
            assertThat(send(patch("/api/after-logs/900").content("{\"inputVersion\":"+value+",\"freeWriting\":\"변경\"}" )).status()).isEqualTo(400);
        }
        for(String value:List.of("\"1\"","1.9","null")){
            assertThat(send(post("/api/after-logs").content("{\"beforeLogId\":900,\"freeWriting\":\"원문\",\"bodySymptomIds\":["+value+"]}" )).status()).isEqualTo(400);
            assertThat(send(patch("/api/after-logs/900").content("{\"inputVersion\":1,\"bodySymptomIds\":["+value+"]}" )).status()).isEqualTo(400);
        }
        assertThat(jdbc.queryForObject("SELECT free_writing FROM after_logs WHERE id=900",String.class)).isEqualTo("원문");
    }
    void assertOneInsight(){assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_jobs WHERE after_log_id=900 AND kind='record_insight'",Integer.class)).isEqualTo(1);}
}
