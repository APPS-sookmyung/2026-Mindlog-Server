package com.apps.mindlog.ai.job;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class JobStore {
    public static final Duration LEASE = Duration.ofMinutes(10);
    public static final Duration RESULT_TTL = Duration.ofMinutes(30);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final JsonMapper json;

    public JobStore(JdbcTemplate jdbc, PlatformTransactionManager manager, Clock clock, JsonMapper json) {
        this.jdbc=jdbc; this.tx=new TransactionTemplate(manager); this.clock=clock; this.json=json;
    }

    /** Caller already holds account/record locks and enforces idempotency and quota. */
    public UUID submit(long userId, long generation, Long beforeId, Long afterId, JobKind kind,
                       long version, UUID requestKey, String input) {
        requireTransaction();
        if (userId <= 0 || generation < 0 || version < 1 || kind == null || requestKey == null
                || (kind == JobKind.BEFORE_REBUTTAL ? afterId != null : afterId == null || beforeId != null))
            throw new IllegalArgumentException("Invalid job scope");
        validateJson(input);
        UUID id=UUID.randomUUID();
        var now=Timestamp.from(clock.instant());
        jdbc.update("""
            INSERT INTO ai_jobs(id,user_id,before_log_id,after_log_id,kind,status,request_key,
                input_version,data_generation,input_snapshot,retryable,created_at,updated_at)
            VALUES(?,?,?,?,?,'PENDING',?,?,?,CAST(? AS jsonb),false,?,?)
            """,id,userId,beforeId,afterId,kind.code(),requestKey,version,generation,input,now,now);
        return id;
    }

    public boolean hasCompleted(long userId,long generation,long afterId,JobKind kind,long version) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM ai_jobs WHERE user_id=? AND data_generation=? AND after_log_id=?
                AND kind=? AND input_version=? AND status='COMPLETED')
            """,Boolean.class,userId,generation,afterId,kind.code(),version));
    }

    public boolean hasActive(long afterId,JobKind kind) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM ai_jobs WHERE after_log_id=? AND kind=? AND status IN ('PENDING','PROCESSING'))",
                Boolean.class,afterId,kind.code()));
    }

    public void invalidateAfter(long afterId,long currentVersion) {
        requireTransaction();
        var now=at(clock.instant());
        jdbc.update("""
            UPDATE ai_jobs SET status='FAILED',failure_reason='STALE_INPUT',retryable=false,
                result=NULL,input_snapshot='{}'::jsonb,attempt_token=NULL,lease_until=NULL,expires_at=NULL,
                completed_at=COALESCE(completed_at,?),updated_at=? WHERE after_log_id=? AND input_version<>?
            """,now,now,afterId,currentVersion);
    }

    public Optional<AsyncJob> find(UUID id) {
        return jdbc.query("SELECT * FROM ai_jobs WHERE id=?",JobStore::map,id).stream().findFirst();
    }

    /** Bounded outbox scan. Re-delivery covers enqueue failure and process crashes. */
    public List<UUID> dispatchable(JobKind kind) {
        return tx.execute(status -> jdbc.query("""
            WITH candidates AS (
                SELECT id FROM ai_jobs WHERE kind=? AND
                    (status='PENDING' OR (status='PROCESSING' AND lease_until<=?))
                    AND (dispatch_after IS NULL OR dispatch_after<=?)
                ORDER BY created_at,id LIMIT 100 FOR UPDATE SKIP LOCKED
            ) UPDATE ai_jobs j SET dispatch_after=? FROM candidates c WHERE j.id=c.id RETURNING j.id
            """,(rs,n)->rs.getObject("id",UUID.class),kind.code(),at(clock.instant()),at(clock.instant()),
                at(clock.instant().plusSeconds(30))));
    }

    public Optional<AsyncJob> claim(UUID id) {
        var now=clock.instant();
        return jdbc.query("""
            UPDATE ai_jobs SET status='PROCESSING',attempt_token=?,lease_until=?,updated_at=?
            WHERE id=? AND (status='PENDING' OR (status='PROCESSING' AND lease_until<=?)) RETURNING *
            """,JobStore::map,UUID.randomUUID(),at(now.plus(LEASE)),at(now),id,at(now)).stream().findFirst();
    }

    public boolean mayCompute(AsyncJob attempt, JobResultGuard guard) {
        return mayCompute(attempt,guard,()->{});
    }

    public boolean mayCompute(AsyncJob attempt, JobResultGuard guard, Runnable started) {
        return Boolean.TRUE.equals(tx.execute(status -> {
            boolean current=guard.lockAndIsCurrent(attempt);
            if (!ownsLiveAttempt(attempt)) return false;
            if (!current) markFailed(attempt,JobFailure.STALE_INPUT);
            else started.run();
            return current;
        }));
    }

    public boolean complete(AsyncJob attempt, String result, JobResultGuard guard, Runnable apply) {
        validateJson(result);
        return Boolean.TRUE.equals(tx.execute(status -> {
            // Lock order is always account -> record -> job, shared with reset and mutation.
            boolean current=guard.lockAndIsCurrent(attempt);
            if (!ownsLiveAttempt(attempt)) return false;
            if (!current) { markFailed(attempt,JobFailure.STALE_INPUT); return false; }
            apply.run();
            Instant now=clock.instant();
            jdbc.update("""
                UPDATE ai_jobs SET status='COMPLETED',result=CAST(? AS jsonb),
                    input_snapshot=CASE WHEN kind='before_rebuttal' THEN input_snapshot ELSE '{}'::jsonb END,
                    failure_reason=NULL,retryable=false,completed_at=?,expires_at=?,updated_at=?,
                    lease_until=NULL,attempt_token=NULL WHERE id=?
                """,result,at(now),attempt.kind().temporary()?at(now.plus(RESULT_TTL)):null,at(now),attempt.id());
            return true;
        }));
    }

    public void fail(AsyncJob attempt, JobFailure reason) {
        tx.executeWithoutResult(status -> { if (ownsLiveAttempt(attempt)) markFailed(attempt,reason); });
    }

    public void fail(AsyncJob attempt,JobFailure reason,JobResultGuard guard,Runnable applyFailure) {
        tx.executeWithoutResult(status->{
            boolean current=guard.lockAndIsCurrent(attempt);
            if(!ownsLiveAttempt(attempt))return;
            if(current)applyFailure.run();
            markFailed(attempt,current?reason:JobFailure.STALE_INPUT);
        });
    }

    private boolean ownsLiveAttempt(AsyncJob attempt) {
        var locked=jdbc.query("SELECT * FROM ai_jobs WHERE id=? FOR UPDATE",JobStore::map,attempt.id());
        if (locked.isEmpty()) return false;
        var job=locked.getFirst();
        return job.status()==JobStatus.PROCESSING && attempt.attemptToken()!=null
                && attempt.attemptToken().equals(job.attemptToken()) && job.leaseUntil()!=null
                && clock.instant().isBefore(job.leaseUntil());
    }

    private void markFailed(AsyncJob attempt, JobFailure reason) {
        var now=at(clock.instant());
        jdbc.update("""
            UPDATE ai_jobs SET status='FAILED',failure_reason=?,retryable=?,input_snapshot='{}'::jsonb,
                result=NULL,completed_at=?,updated_at=?,lease_until=NULL,attempt_token=NULL WHERE id=?
            """,reason.name(),reason.retryable(),now,now,attempt.id());
    }

    /** Expired metadata stays available to final-diary confirmation. */
    public int purgeExpiredBodies() {
        return jdbc.update("UPDATE ai_jobs SET result=NULL,input_snapshot='{}'::jsonb WHERE expires_at<=? AND result IS NOT NULL",
                at(clock.instant()));
    }

    /** Reset caller holds account lock and increments data_generation in this transaction. */
    public void deleteForUser(long userId) {
        requireTransaction();
        jdbc.update("DELETE FROM ai_jobs WHERE user_id=?",userId);
    }

    private void validateJson(String value) {
        if (value==null || !json.readTree(value).isObject()) throw new IllegalArgumentException("JSON object required");
    }
    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("An account/record transaction is required");
    }
    private static Timestamp at(Instant value) { return value==null?null:Timestamp.from(value); }
    private static Instant instant(ResultSet rs,String name) throws SQLException {
        Timestamp value=rs.getTimestamp(name); return value==null?null:value.toInstant();
    }
    private static AsyncJob map(ResultSet rs,int row) throws SQLException {
        return new AsyncJob(rs.getObject("id",UUID.class),rs.getLong("user_id"),
                rs.getObject("before_log_id",Long.class),rs.getObject("after_log_id",Long.class),
                JobKind.fromCode(rs.getString("kind")),JobStatus.valueOf(rs.getString("status")),
                rs.getObject("request_key",UUID.class),rs.getLong("input_version"),rs.getLong("data_generation"),
                rs.getString("input_snapshot"),rs.getString("result"),rs.getString("failure_reason"),
                rs.getBoolean("retryable"),instant(rs,"expires_at"),instant(rs,"completed_at"),instant(rs,"created_at"),
                rs.getObject("attempt_token",UUID.class),instant(rs,"lease_until"));
    }
}
