package com.apps.mindlog.ai.common;

import com.apps.mindlog.ai.job.JobKind;
import com.apps.mindlog.ai.job.JobStore;
import com.apps.mindlog.global.error.ErrorType;
import com.apps.mindlog.global.error.exception.ApiException;
import com.apps.mindlog.global.idempotency.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class LlmQuotaGuardTest {
    @Container static final PostgreSQLContainer DB=new PostgreSQLContainer("postgres:16-alpine");
    static JdbcTemplate jdbc;
    static DataSourceTransactionManager manager;
    static TransactionTemplate tx;
    static MutableClock clock=new MutableClock();
    LlmQuotaGuard quota;
    static final Instant NOW=Instant.parse("2026-09-30T00:59:59.100Z");
    @BeforeAll static void setup(){
        var source=new DriverManagerDataSource(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword());
        Flyway.configure().dataSource(source).load().migrate();jdbc=new JdbcTemplate(source);
        manager=new DataSourceTransactionManager(source);tx=new TransactionTemplate(manager);
    }
    @BeforeEach void fixture(){
        clock.now=NOW;quota=new LlmQuotaGuard(jdbc,clock,1);
        jdbc.execute("TRUNCATE users CASCADE");
        for(int id=1;id<=2;id++)jdbc.update("""
            INSERT INTO users(id,email,nickname,representative_character_id,account_status,
                after_reminder_enabled,weekly_report_start_day,created_at,updated_at)
            VALUES(?,?,'테스트',1,'active',true,'sun',?,?)
            """,id,id+"@quota.example",Timestamp.from(NOW),Timestamp.from(NOW));
    }
    void consume(long user){tx.executeWithoutResult(s->quota.consume(user));}
    @Test void independentUserLimitsResetAtHourBoundaryWithCeilingRetryAfter(){
        consume(1);consume(2);
        assertThatThrownBy(()->consume(1)).isInstanceOfSatisfying(ApiException.class,e->{
            assertThat(e.getErrorType()).isEqualTo(ErrorType.RATE_LIMITED);
            assertThat(e.getRetryAfterSeconds().orElseThrow()).isEqualTo(1);
        });
        clock.now=Instant.parse("2026-09-30T01:00:00Z");consume(1);
        assertThat(jdbc.queryForObject("SELECT used FROM llm_quota_usage WHERE user_id=1",Integer.class)).isEqualTo(1);
    }
    @Test void rollbackDoesNotConsumeAndMissingTransactionIsRejected(){
        assertThatIllegalStateException().isThrownBy(()->quota.consume(1));
        assertThatThrownBy(()->tx.executeWithoutResult(s->{quota.consume(1);throw new IllegalStateException();}));
        consume(1);
    }
    @Test void simultaneousRequestsCannotExceedLimit()throws Exception{
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()){
            var start=new CountDownLatch(1);
            Callable<Boolean> request=()->{start.await();try{consume(1);return true;}catch(ApiException e){return false;}};
            var a=pool.submit(request);var b=pool.submit(request);start.countDown();
            assertThat(a.get(10,TimeUnit.SECONDS)^b.get(10,TimeUnit.SECONDS)).isTrue();
            assertThat(jdbc.queryForObject("SELECT used FROM llm_quota_usage WHERE user_id=1",Integer.class)).isEqualTo(1);
        }
    }
    @Test void sameIdempotencyKeyReplayDoesNotConsumeAgain(){
        var idempotency=new IdempotencyService(jdbc,manager,JsonMapper.builder().build(),clock);
        var scope=new IdempotencyService.Scope(1,0,"test:analysis");
        var result=new IdempotencyRecord(202,"{}",Map.of("Retry-After","2"));
        for(int i=0;i<2;i++)assertThat(idempotency.execute(scope,"key","{}",()->{},()->{quota.consume(1);return result;})).isEqualTo(result);
        assertThat(jdbc.queryForObject("SELECT used FROM llm_quota_usage WHERE user_id=1",Integer.class)).isEqualTo(1);
    }
    @Test void beforeDraftReplaysTheOriginalJobAndRejectsKeyReuseWithChangedInput() {
        var mapper = JsonMapper.builder().build();
        var idempotency = new IdempotencyService(jdbc, manager, mapper, clock);
        var store = new JobStore(jdbc, manager, clock, mapper);
        var scope = new IdempotencyService.Scope(1, 0, "before:rebuttal");
        quota = new LlmQuotaGuard(jdbc, clock, 2);
        BiFunction<String, String, IdempotencyRecord> submit = (key, body) ->
                idempotency.execute(scope, key, body, () -> {}, () -> {
                    quota.consume(1);
                    long version = mapper.readTree(body).get("inputVersion").longValue();
                    UUID job = store.submit(1, 0, null, null, JobKind.BEFORE_REBUTTAL, version, UUID.randomUUID(), body);
                    return new IdempotencyRecord(202, mapper.writeValueAsString(Map.of("jobId", job)));
                });
        String original = "{\"inputVersion\":1,\"worstScenario\":\"발표가 걱정돼\"}";
        String edited = "{\"inputVersion\":2,\"worstScenario\":\"질문이 걱정돼\"}";
        var first = submit.apply("initial", original);
        assertThat(submit.apply("initial", original)).isEqualTo(first);
        assertThatThrownBy(() -> submit.apply("initial", edited)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getErrorType()).isEqualTo(ErrorType.IDEMPOTENCY_CONFLICT));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_jobs WHERE user_id=1", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT used FROM llm_quota_usage WHERE user_id=1", Integer.class)).isEqualTo(1);

        var second = submit.apply("edited", edited);
        assertThat(second.body()).isNotEqualTo(first.body());
        assertThat(jdbc.queryForList("SELECT input_version FROM ai_jobs WHERE user_id=1 ORDER BY input_version", Long.class))
                .containsExactly(1L, 2L);
        assertThat(jdbc.queryForObject("SELECT used FROM llm_quota_usage WHERE user_id=1", Integer.class)).isEqualTo(2);
    }

    @Test void resetDoesNotBypassQuotaAndWithdrawalCleansUsage(){
        consume(1);jdbc.update("UPDATE users SET data_generation=1 WHERE id=1");
        assertThatThrownBy(()->consume(1)).isInstanceOf(ApiException.class);
        jdbc.update("DELETE FROM users WHERE id=1");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM llm_quota_usage",Integer.class)).isZero();
    }
    @Test void undecidedZeroLimitBlocksAndClockReversalDoesNotResetQuota(){
        quota=new LlmQuotaGuard(jdbc,clock,0);
        assertThatThrownBy(()->consume(1)).isInstanceOf(ApiException.class);
        quota=new LlmQuotaGuard(jdbc,clock,1);consume(1);clock.now=NOW.minusSeconds(3600);
        assertThatThrownBy(()->consume(1)).isInstanceOf(ApiException.class);
        assertThatIllegalArgumentException().isThrownBy(()->new LlmQuotaGuard(jdbc,clock,-1));
    }
    static class MutableClock extends Clock{
        Instant now;public Instant instant(){return now;}public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return this;}
    }
}
