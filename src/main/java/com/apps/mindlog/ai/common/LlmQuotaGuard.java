package com.apps.mindlog.ai.common;

import com.apps.mindlog.global.error.ErrorType;
import com.apps.mindlog.global.error.exception.ApiException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class LlmQuotaGuard {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final int maximum;
    public LlmQuotaGuard(JdbcTemplate jdbc,Clock clock,
            @Value("${mindlog.ai.max-requests-per-hour:0}") int maximum){
        if(maximum<0)throw new IllegalArgumentException("Quota must not be negative");
        this.jdbc=jdbc;this.clock=clock;this.maximum=maximum;
    }
    /** User requests reserve before submit; internal record insights reserve per execution attempt. */
    public void consume(long userId){
        if(!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Job submission transaction required");
        if(userId<=0)throw new IllegalArgumentException("Authenticated user required");
        var now=clock.instant();
        var bucket=now.truncatedTo(ChronoUnit.HOURS);
        long retry=bucket.plus(1,ChronoUnit.HOURS).getEpochSecond()-now.getEpochSecond();
        if(maximum==0)throw limited(retry);
        var reserved=jdbc.queryForList("""
            INSERT INTO llm_quota_usage(user_id,bucket_start,used) VALUES(?,?,1)
            ON CONFLICT(user_id) DO UPDATE SET
                used=CASE WHEN llm_quota_usage.bucket_start<EXCLUDED.bucket_start THEN 1 ELSE llm_quota_usage.used+1 END,
                bucket_start=GREATEST(llm_quota_usage.bucket_start,EXCLUDED.bucket_start)
            WHERE llm_quota_usage.bucket_start<EXCLUDED.bucket_start OR
                (llm_quota_usage.bucket_start=EXCLUDED.bucket_start AND llm_quota_usage.used<?)
            RETURNING used
            """,Integer.class,userId,Timestamp.from(bucket),maximum);
        if(reserved.isEmpty())throw limited(retry);
    }
    private static ApiException limited(long seconds){
        return new ApiException(ErrorType.RATE_LIMITED,"AI 요청 한도에 도달했습니다. 잠시 후 다시 시도해 주세요.",seconds);
    }
}
