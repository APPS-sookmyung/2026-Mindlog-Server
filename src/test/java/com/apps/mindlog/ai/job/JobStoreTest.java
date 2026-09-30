package com.apps.mindlog.ai.job;

import java.sql.Timestamp;
import java.time.*;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
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
class JobStoreTest {
    @Container static final PostgreSQLContainer DB=new PostgreSQLContainer("postgres:16-alpine");
    static JdbcTemplate jdbc;
    static TransactionTemplate tx;
    static JobStore store;
    static MutableClock clock=new MutableClock();
    static final Instant NOW=Instant.parse("2026-09-30T00:00:00Z");
    @BeforeAll static void setup() {
        var source=new DriverManagerDataSource(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword());
        Flyway.configure().dataSource(source).load().migrate();
        jdbc=new JdbcTemplate(source);
        var manager=new DataSourceTransactionManager(source);
        tx=new TransactionTemplate(manager);
        store=new JobStore(jdbc,manager,clock,JsonMapper.builder().build());
        jdbc.execute("CREATE TABLE test_effects(value integer)");
    }
    @BeforeEach void fixture() {
        clock.now=NOW;
        jdbc.execute("TRUNCATE users,test_effects CASCADE");
        jdbc.update("""
            INSERT INTO users(id,email,nickname,representative_character_id,account_status,
                after_reminder_enabled,weekly_report_start_day,created_at,updated_at)
            VALUES(1,'job@test.example','테스트',1,'active',true,'sun',?,?)
            """,Timestamp.from(NOW),Timestamp.from(NOW));
        jdbc.update("""
            INSERT INTO before_logs(id,user_id,situation_type_id,scheduled_at,expected_score,worst_scenario,created_at,updated_at)
            VALUES(1,1,1,'2026-09-30',50,'테스트',?,?)
            """,Timestamp.from(NOW),Timestamp.from(NOW));
        jdbc.update("""
            INSERT INTO after_logs(id,before_log_id,free_writing,record_status,created_at,updated_at)
            VALUES(1,1,'비공개 원문','DRAFT',?,?)
            """,Timestamp.from(NOW),Timestamp.from(NOW));
    }
    UUID submit(JobKind kind) {
        return tx.execute(status -> store.submit(1,0,null,kind==JobKind.BEFORE_REBUTTAL?null:1L,
                kind,1,UUID.randomUUID(),"{\"privateText\":\"원문\"}"));
    }
    @Test void submissionRequiresTransactionAndRollsBackWithDomain() {
        assertThatIllegalStateException().isThrownBy(()->store.submit(1,0,null,1L,JobKind.DIARY_DRAFT,1,UUID.randomUUID(),"{}"));
        assertThatThrownBy(()->tx.executeWithoutResult(s->{submit(JobKind.DIARY_DRAFT);throw new IllegalStateException();}));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_jobs",Integer.class)).isZero();
    }
    @Test void duplicateQueueDeliveriesClaimOnlyOnce() throws Exception {
        var id=submit(JobKind.AFTER_ANALYSIS);
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            var start=new CountDownLatch(1);
            var a=pool.submit(()->{start.await();return store.claim(id);});
            var b=pool.submit(()->{start.await();return store.claim(id);});
            start.countDown();
            assertThat(List.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS)).stream().filter(java.util.Optional::isPresent)).hasSize(1);
        }
    }
    @Test void expiredLeaseIsRecoveredAndOldAttemptCannotApply() {
        var id=submit(JobKind.AFTER_ANALYSIS);
        var old=store.claim(id).orElseThrow();
        clock.now=NOW.plus(JobStore.LEASE);
        var replacement=store.claim(id).orElseThrow();
        AtomicInteger applied=new AtomicInteger();
        assertThat(store.complete(old,"{}",j->true,applied::incrementAndGet)).isFalse();
        store.fail(old,JobFailure.PROVIDER_UNAVAILABLE);
        assertThat(store.complete(replacement,"{}",j->true,applied::incrementAndGet)).isTrue();
        assertThat(applied).hasValue(1);
    }
    @Test void lateResultAfterVersionChangeIsDiscarded() {
        var attempt=store.claim(submit(JobKind.AFTER_ANALYSIS)).orElseThrow();
        jdbc.update("UPDATE after_logs SET input_version=2 WHERE id=1");
        assertThat(store.complete(attempt,"{}",this::guard,()->jdbc.update("INSERT INTO test_effects VALUES(1)"))).isFalse();
        assertThat(store.find(attempt.id()).orElseThrow().failureReason()).isEqualTo("STALE_INPUT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM test_effects",Integer.class)).isZero();
    }
    @Test void generationChangeAndWithdrawalDiscardResults() {
        var attempt=store.claim(submit(JobKind.AFTER_ANALYSIS)).orElseThrow();
        jdbc.update("UPDATE users SET data_generation=1 WHERE id=1");
        assertThat(store.mayCompute(attempt,this::guard)).isFalse();
        var other=store.claim(submit(JobKind.DIARY_DRAFT)).orElseThrow();
        jdbc.update("DELETE FROM users WHERE id=1");
        assertThat(store.complete(other,"{}",this::guard,()->fail("must not apply"))).isFalse();
    }
    @Test void applyFailureRollsBackBothResultAndDomainWrite() {
        var attempt=store.claim(submit(JobKind.AFTER_ANALYSIS)).orElseThrow();
        assertThatThrownBy(()->store.complete(attempt,"{}",this::guard,()->{
            jdbc.update("INSERT INTO test_effects VALUES(1)");throw new IllegalStateException();
        }));
        assertThat(store.find(attempt.id()).orElseThrow().status()).isEqualTo(JobStatus.PROCESSING);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM test_effects",Integer.class)).isZero();
    }
    @Test void temporaryResultExpiresAtThirtyMinutesButMetadataRemains() {
        for (var kind:List.of(JobKind.BEFORE_REBUTTAL,JobKind.DIARY_DRAFT)) {
            var attempt=store.claim(submit(kind)).orElseThrow();
            assertThat(store.complete(attempt,"{\"content\":\"일기\"}",j->true,()->{})).isTrue();
            if(kind==JobKind.BEFORE_REBUTTAL)
                assertThat(store.find(attempt.id()).orElseThrow().inputSnapshot()).contains("privateText");
            clock.now=NOW.plusSeconds(1799);
            assertThat(store.find(attempt.id()).orElseThrow().expired(clock.instant())).isFalse();
            assertThat(store.purgeExpiredBodies()).isZero();
            clock.now=NOW.plusSeconds(1800);
            assertThat(store.purgeExpiredBodies()).isEqualTo(1);
            var metadata=store.find(attempt.id()).orElseThrow();
            assertThat(metadata.status()).isEqualTo(JobStatus.COMPLETED);
            assertThat(metadata.result()).isNull();
            assertThat(metadata.inputSnapshot()).isEqualTo("{}");
            assertThat(metadata.completedAt()).isEqualTo(NOW);
            assertThat(metadata.inputVersion()).isEqualTo(1);
            clock.now=NOW;
        }
    }
    @Test void outboxRedeliveryCoversFailedEnqueueButSkipsLiveLease() {
        var id=submit(JobKind.AFTER_ANALYSIS);
        assertThat(store.dispatchable(JobKind.AFTER_ANALYSIS)).containsExactly(id);
        assertThat(store.dispatchable(JobKind.AFTER_ANALYSIS)).isEmpty();
        clock.now=NOW.plusSeconds(30);
        assertThat(store.dispatchable(JobKind.AFTER_ANALYSIS)).containsExactly(id);
        store.claim(id);
        clock.now=clock.now.plusSeconds(30);
        assertThat(store.dispatchable(JobKind.AFTER_ANALYSIS)).isEmpty();
        clock.now=clock.now.plus(JobStore.LEASE);
        assertThat(store.dispatchable(JobKind.AFTER_ANALYSIS)).containsExactly(id);
    }
    @Test void resetDeletesMetadataAndCompletedDurableResultsDoNotExpire() {
        var attempt=store.claim(submit(JobKind.AFTER_ANALYSIS)).orElseThrow();
        store.complete(attempt,"{}",this::guard,()->{});
        clock.now=NOW.plus(Duration.ofDays(1));
        assertThat(store.purgeExpiredBodies()).isZero();
        assertThat(store.find(attempt.id()).orElseThrow().result()).isNotNull();
        assertThatIllegalStateException().isThrownBy(()->store.deleteForUser(1));
        tx.executeWithoutResult(s->store.deleteForUser(1));
        assertThat(store.find(attempt.id())).isEmpty();
    }
    @Test void activeAfterKindUniqueConstraintAllowsDifferentKinds() {
        submit(JobKind.AFTER_ANALYSIS);
        assertThatThrownBy(()->submit(JobKind.AFTER_ANALYSIS)).isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        assertThat(submit(JobKind.DIARY_DRAFT)).isNotNull();
    }
    @Test void executorAppliesOnceAndDoesNotLeakFailureMessage() {
        var calls=new AtomicInteger();
        JobHandler handler=new JobHandler() {
            public JobKind kind(){return JobKind.AFTER_ANALYSIS;}
            public boolean lockAndIsCurrent(AsyncJob job){return guard(job);}
            public String compute(AsyncJob job){calls.incrementAndGet();return "{}";}
            public void apply(AsyncJob job,String result){jdbc.update("INSERT INTO test_effects VALUES(1)");}
        };
        var executor=new JobExecutor(store,List.of(handler));
        var id=submit(JobKind.AFTER_ANALYSIS);
        executor.run(id);executor.run(id);
        assertThat(calls).hasValue(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM test_effects",Integer.class)).isEqualTo(1);
        assertThat(store.find(id).orElseThrow().toString()).doesNotContain("원문","privateText");
    }
    @Test void providerFailureIsSafeAndMissingHandlerLeavesJobPending() {
        var id=submit(JobKind.AFTER_ANALYSIS);
        new JobExecutor(store,List.of()).run(id);
        assertThat(store.find(id).orElseThrow().status()).isEqualTo(JobStatus.PENDING);
        JobHandler handler=new JobHandler() {
            public JobKind kind(){return JobKind.AFTER_ANALYSIS;}
            public boolean lockAndIsCurrent(AsyncJob job){return guard(job);}
            public String compute(AsyncJob job){throw new RuntimeException("private provider body and key");}
            public void apply(AsyncJob job,String result){fail("must not apply");}
        };
        new JobExecutor(store,List.of(handler)).run(id);
        var job=store.find(id).orElseThrow();
        assertThat(job.status()).isEqualTo(JobStatus.FAILED);
        assertThat(job.failureReason()).isEqualTo("PROVIDER_UNAVAILABLE");
        assertThat(job.retryable()).isTrue();
        assertThat(job.inputSnapshot()).isEqualTo("{}");
    }
    @Test void staleWorkDoesNotCallProviderAndMalformedOutputCannotComplete() {
        var calls=new AtomicInteger();
        JobHandler handler=new JobHandler() {
            public JobKind kind(){return JobKind.AFTER_ANALYSIS;}
            public boolean lockAndIsCurrent(AsyncJob job){return false;}
            public String compute(AsyncJob job){calls.incrementAndGet();return "{}";}
            public void apply(AsyncJob job,String result){fail("must not apply");}
        };
        new JobExecutor(store,List.of(handler)).run(submit(JobKind.AFTER_ANALYSIS));
        assertThat(calls).hasValue(0);
        var attempt=store.claim(submit(JobKind.DIARY_DRAFT)).orElseThrow();
        assertThatIllegalArgumentException().isThrownBy(()->store.complete(attempt,"[]",j->true,()->fail("must not apply")));
        assertThat(store.find(attempt.id()).orElseThrow().status()).isEqualTo(JobStatus.PROCESSING);
    }
    boolean guard(AsyncJob job) {
        var users=jdbc.queryForList("SELECT data_generation FROM users WHERE id=? AND account_status='active' FOR UPDATE",Long.class,job.userId());
        if(users.isEmpty()||users.getFirst()!=job.dataGeneration())return false;
        return jdbc.queryForObject("SELECT input_version FROM after_logs WHERE id=? FOR UPDATE",Long.class,job.afterLogId())==job.inputVersion();
    }
    static class MutableClock extends Clock {
        Instant now;
        public ZoneId getZone(){return ZoneId.of("Asia/Seoul");}
        public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){return now;}
    }
}
