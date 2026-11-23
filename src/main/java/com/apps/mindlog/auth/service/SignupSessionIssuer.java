package com.apps.mindlog.auth.service;

import com.apps.mindlog.global.security.AccessTokenCodec;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Issues the initial session as part of signup; login and refresh endpoints remain separate work. */
@Service
public class SignupSessionIssuer {
    public static final long ACCESS_TOKEN_SECONDS = 3600;
    private static final Duration REFRESH_LIFETIME = Duration.ofDays(30);
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate jdbc;
    private final AccessTokenCodec tokens;
    private final Clock clock;

    public SignupSessionIssuer(JdbcTemplate jdbc, AccessTokenCodec tokens, Clock clock) {
        this.jdbc = jdbc; this.tokens = tokens; this.clock = clock;
    }

    @Transactional
    public IssuedSession issue(long userId) {
        return issue(userId, null);
    }

    @Transactional
    public IssuedSession issue(long userId, String deviceToken) {
        Instant now = Instant.now(clock);
        byte[] bytes = new byte[48]; RANDOM.nextBytes(bytes);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Long deviceTokenId = null;
        if (deviceToken != null) {
            String normalizedToken = deviceToken.strip();
            if (normalizedToken.isEmpty()) throw new IllegalArgumentException("deviceToken must not be blank");
            deviceTokenId = jdbc.query("""
                    INSERT INTO device_tokens(user_id,token,platform,created_at,updated_at)
                    VALUES(?,?,'web',?,?) ON CONFLICT(token) DO UPDATE SET updated_at=EXCLUDED.updated_at
                    WHERE device_tokens.user_id=EXCLUDED.user_id
                    RETURNING id
                    """, rs -> { if (!rs.next()) throw new IllegalArgumentException("deviceToken is already registered to another account"); return rs.getLong(1); }, userId, normalizedToken,
                    java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
        }
        jdbc.update("""
                INSERT INTO refresh_sessions(user_id,device_token_id,refresh_token_hash,expires_at,created_at)
                VALUES(?,?,?,?,?)
                """, userId, deviceTokenId, tokens.refreshTokenHash(refreshToken),
                java.sql.Timestamp.from(now.plus(REFRESH_LIFETIME)), java.sql.Timestamp.from(now));
        return new IssuedSession(tokens.issue(userId, now), refreshToken, ACCESS_TOKEN_SECONDS);
    }

    public record IssuedSession(String accessToken, String refreshToken, long expiresIn) { }
}
