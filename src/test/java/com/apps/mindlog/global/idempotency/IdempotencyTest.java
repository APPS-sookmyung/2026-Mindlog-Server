package com.apps.mindlog.global.idempotency;

import com.apps.mindlog.global.error.ErrorType;
import com.apps.mindlog.global.error.exception.ApiException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.*;

@Testcontainers
class IdempotencyTest {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");
    static JdbcTemplate jdbc;
    static DataSourceTransactionManager manager;
    static IdempotencyService service;
    static final Instant NOW = Instant.parse("2026-09-29T15:00:00Z");
    static final IdempotencyService.Scope SCOPE = new IdempotencyService.Scope(1, 0, "after:1:finalize");
    static final IdempotencyRecord RESULT = new IdempotencyRecord(201, "{\"id\":7}",
            Map.of("Content-Type", "application/json", "Location", "/api/after-logs/7"));
    AtomicInteger calls = new AtomicInteger();

    @BeforeAll static void setup() {
        var source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(source).load().migrate();
        jdbc = new JdbcTemplate(source);
        manager = new DataSourceTransactionManager(source);
        service = new IdempotencyService(jdbc, manager, JsonMapper.builder().build(),
                Clock.fixed(NOW, ZoneId.of("Asia/Seoul")));
        jdbc.execute("CREATE TABLE test_effects(value integer)");
    }

    @BeforeEach void fixtures() {
        jdbc.execute("TRUNCATE users,emotion_characters,test_effects CASCADE");
        jdbc.update("""
                INSERT INTO emotion_characters(id,code,name,display_order,active,created_at,updated_at)
                VALUES(1,'neutral','보통',1,true,?,?)
                """, Timestamp.from(NOW), Timestamp.from(NOW));
        for (int id = 1; id <= 2; id++) jdbc.update("""
                INSERT INTO users(id,email,nickname,representative_character_id,account_status,
                    after_reminder_enabled,weekly_report_start_day,created_at,updated_at)
                VALUES(?,?,'테스트',1,'active',true,'sun',?,?)
                """, id, id + "@test.example", Timestamp.from(NOW), Timestamp.from(NOW));
    }

    @Test void replaysExactResultAndNormalizesObjectOrderButPreservesArrayOrder() {
        var first = execute(SCOPE, "key", "{\"nested\":{\"a\":1,\"b\":2},\"list\":[1,2]}");
        var second = execute(SCOPE, "key", "{ \"list\": [1,2], \"nested\": {\"b\":2,\"a\":1} }");
        assertThat(first).isEqualTo(RESULT).isEqualTo(second);
        assertThat(calls).hasValue(1);
        assertConflict(() -> execute(SCOPE, "key", "{\"nested\":{\"a\":1,\"b\":2},\"list\":[2,1]}"));
        assertThat(jdbc.queryForObject("SELECT created_at FROM idempotency_records", Timestamp.class).toInstant()).isEqualTo(NOW);
    }

    @Test void highPrecisionNumbersDoNotCollapseToOneHash() {
        execute(SCOPE, "key", "{\"number\":0.123456789012345678901}");
        assertConflict(() -> execute(SCOPE, "key", "{\"number\":0.123456789012345678902}"));
    }

    @Test void malformedDuplicateAndTrailingJsonAreRejectedBeforeAnyWrite() {
        for (String input : new String[]{"{", "{\"a\":1,\"a\":2}", "{} {}", null}) {
            assertThatThrownBy(() -> execute(SCOPE, "key", input)).isInstanceOfSatisfying(ApiException.class,
                    failure -> assertThat(failure.getErrorType()).isEqualTo(ErrorType.VALIDATION_ERROR));
        }
        assertThat(count("idempotency_records")).isZero();
        assertThat(calls).hasValue(0);
    }

    @Test void scopeSeparatesUsersOperationsAndGenerations() {
        execute(SCOPE, "key", "{}");
        execute(new IdempotencyService.Scope(2, 0, SCOPE.operation()), "key", "{}");
        execute(new IdempotencyService.Scope(1, 0, "after:2:finalize"), "key", "{}");
        jdbc.update("UPDATE users SET data_generation=1 WHERE id=1");
        execute(new IdempotencyService.Scope(1, 1, SCOPE.operation()), "key", "{}");
        assertThat(calls).hasValue(4);
        assertThatThrownBy(() -> execute(SCOPE, "key", "{}")).isInstanceOf(ApiException.class);
    }

    @Test void checksAccessBeforeReplayingAndDeletesCachedPersonalDataOnResetOrWithdrawal() {
        execute(SCOPE, "key", "{}");
        assertThatThrownBy(() -> service.execute(SCOPE, "key", "{}",
                () -> { throw new ApiException(ErrorType.RESOURCE_NOT_FOUND); }, () -> RESULT))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.deleteForUser(1)).isInstanceOf(IllegalStateException.class);
        new TransactionTemplate(manager).executeWithoutResult(tx -> service.deleteForUser(1));
        assertThat(count("idempotency_records")).isZero();
        execute(SCOPE, "key", "{}");
        jdbc.update("DELETE FROM users WHERE id=1");
        assertThat(count("idempotency_records")).isZero();
    }

    @Test void rollsBackBusinessWriteAndReservationWhenActionFails() {
        assertThatThrownBy(() -> service.execute(SCOPE, "key", "{}", () -> access(SCOPE), () -> {
            jdbc.update("INSERT INTO test_effects VALUES (1)");
            throw new IllegalStateException("simulated failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(count("test_effects")).isZero();
        assertThat(count("idempotency_records")).isZero();
        assertThat(execute(SCOPE, "key", "{}")).isEqualTo(RESULT);
    }

    @Test void concurrentIdenticalRequestsRunActionOnce() throws Exception {
        var bothEntered = new CountDownLatch(2);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var task = (java.util.concurrent.Callable<IdempotencyRecord>) () ->
                    service.execute(SCOPE, "concurrent", "{}", () -> {
                        access(SCOPE);
                        bothEntered.countDown();
                        try {
                            if (!bothEntered.await(10, TimeUnit.SECONDS)) throw new AssertionError("Second request did not enter");
                        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                    }, () -> { calls.incrementAndGet(); jdbc.update("INSERT INTO test_effects VALUES (1)"); return RESULT; });
            var first = executor.submit(task);
            var second = executor.submit(task);
            assertThat(first.get(20, TimeUnit.SECONDS)).isEqualTo(second.get(20, TimeUnit.SECONDS)).isEqualTo(RESULT);
        }
        assertThat(calls).hasValue(1);
        assertThat(count("test_effects")).isEqualTo(1);
        assertThat(count("idempotency_records")).isEqualTo(1);
    }

    @Test void failingOuterTransactionDoesNotCommitSavedResponse() {
        assertThatThrownBy(() -> new TransactionTemplate(manager).execute(tx -> {
            execute(SCOPE, "key", "{}");
            throw new IllegalStateException("outer failed");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(count("idempotency_records")).isZero();
    }

    @Test void responseCannotPersistSecretsInUnapprovedHeadersOrFailureStatus() {
        assertThatThrownBy(() -> new IdempotencyRecord(401, "{}")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IdempotencyRecord(200, "{}", Map.of("Set-Cookie", "secret")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private IdempotencyRecord execute(IdempotencyService.Scope scope, String key, String input) {
        return service.execute(scope, key, input, () -> access(scope), () -> { calls.incrementAndGet(); return RESULT; });
    }

    // Simulates the domain's access contract. Production callers must perform this check in their own service.
    private void access(IdempotencyService.Scope scope) {
        Long generation = jdbc.queryForObject("SELECT data_generation FROM users WHERE id=? FOR SHARE", Long.class, scope.userId());
        if (generation != scope.dataGeneration()) throw new ApiException(ErrorType.RESOURCE_NOT_FOUND);
    }

    private int count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class); }
    private void assertConflict(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ApiException.class,
                failure -> assertThat(failure.getErrorType()).isEqualTo(ErrorType.IDEMPOTENCY_CONFLICT));
    }
}
