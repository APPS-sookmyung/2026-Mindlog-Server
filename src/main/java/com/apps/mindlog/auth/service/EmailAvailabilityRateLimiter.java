package com.apps.mindlog.auth.service;

import com.apps.mindlog.global.error.ErrorType;
import com.apps.mindlog.global.error.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Distributed per-client-IP fixed-window limiter for the public email lookup endpoint. */
@Component
public class EmailAvailabilityRateLimiter {
    private static final long WINDOW_SECONDS = 60;
    private static final int MAX_REQUESTS = 10;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public EmailAvailabilityRateLimiter(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public void checkAllowed(String remoteAddress) {
        long now = Instant.now(clock).getEpochSecond();
        long windowStart = now - Math.floorMod(now, WINDOW_SECONDS);
        int requestCount = jdbc.queryForObject("""
                INSERT INTO request_rate_limits(client_key, window_started_at, request_count)
                VALUES (?, to_timestamp(?), 1)
                ON CONFLICT (client_key) DO UPDATE SET
                    window_started_at = CASE
                        WHEN request_rate_limits.window_started_at < to_timestamp(?) THEN to_timestamp(?)
                        ELSE request_rate_limits.window_started_at END,
                    request_count = CASE
                        WHEN request_rate_limits.window_started_at < to_timestamp(?) THEN 1
                        ELSE request_rate_limits.request_count + 1 END
                RETURNING request_count
                """, Integer.class, digest(remoteAddress), windowStart, windowStart, windowStart, windowStart);

        if (requestCount > MAX_REQUESTS) {
            long retryAfter = WINDOW_SECONDS - Math.floorMod(now, WINDOW_SECONDS);
            throw new ApiException(ErrorType.RATE_LIMITED, "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.", retryAfter);
        }
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
