package com.apps.mindlog.auth.service;

import com.apps.mindlog.auth.dto.RefreshSessionResponse;
import com.apps.mindlog.global.error.ErrorType;
import com.apps.mindlog.global.error.exception.ApiException;
import com.apps.mindlog.global.security.AccessTokenCodec;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionLifecycleService {
    private static final Duration REFRESH_LIFETIME = Duration.ofDays(30);
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate jdbc;
    private final AccessTokenCodec tokens;
    private final Clock clock;

    public SessionLifecycleService(JdbcTemplate jdbc, AccessTokenCodec tokens, Clock clock) {
        this.jdbc = jdbc; this.tokens = tokens; this.clock = clock;
    }

    @Transactional
    public RefreshSessionResponse refresh(String refreshToken) {
        Session session = jdbc.query("""
                SELECT id,user_id,device_token_id,expires_at,revoked_at FROM refresh_sessions
                WHERE refresh_token_hash=? FOR UPDATE
                """, rs -> rs.next() ? new Session(rs.getLong(1), rs.getLong(2),
                (Long) rs.getObject(3), rs.getTimestamp(4).toInstant(), rs.getTimestamp(5)) : null,
                tokens.refreshTokenHash(refreshToken));
        Instant now = Instant.now(clock);
        if (session == null || session.revokedAt() != null)
            throw new ApiException(ErrorType.INVALID_REFRESH_TOKEN, "Refresh Token이 유효하지 않습니다.");
        if (!session.expiresAt().isAfter(now))
            throw new ApiException(ErrorType.EXPIRED_REFRESH_TOKEN, "Refresh Token이 만료되었습니다.");
        boolean active = Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM users WHERE id=? AND account_status IN ('active','onboarding'))", Boolean.class, session.userId()));
        if (!active) throw new ApiException(ErrorType.INVALID_REFRESH_TOKEN, "Refresh Token이 유효하지 않습니다.");
        String replacement = newRefreshToken();
        int revoked = jdbc.update("UPDATE refresh_sessions SET revoked_at=? WHERE id=? AND revoked_at IS NULL",
                Timestamp.from(now), session.id());
        if (revoked != 1) throw new ApiException(ErrorType.INVALID_REFRESH_TOKEN, "Refresh Token이 유효하지 않습니다.");
        Long newSessionId = jdbc.queryForObject("""
                INSERT INTO refresh_sessions(user_id,device_token_id,refresh_token_hash,expires_at,created_at)
                VALUES(?,?,?,?,?) RETURNING id
                """, Long.class, session.userId(), session.deviceTokenId(), tokens.refreshTokenHash(replacement),
                Timestamp.from(now.plus(REFRESH_LIFETIME)), Timestamp.from(now));
        return new RefreshSessionResponse(tokens.issue(session.userId(), newSessionId, now), replacement,
                SignupSessionIssuer.ACCESS_TOKEN_SECONDS);
    }

    @Transactional
    public void logout(long userId, long sessionId) {
        Instant now = Instant.now(clock);
        Session session = jdbc.query("""
                SELECT id,user_id,device_token_id,expires_at,revoked_at FROM refresh_sessions
                WHERE id=? AND user_id=? FOR UPDATE
                """, rs -> rs.next() ? new Session(rs.getLong(1), rs.getLong(2),
                (Long) rs.getObject(3), rs.getTimestamp(4).toInstant(), rs.getTimestamp(5)) : null,
                sessionId, userId);
        if (session == null || session.revokedAt() != null)
            throw new ApiException(ErrorType.INVALID_TOKEN, "현재 로그인 세션이 유효하지 않습니다.");
        Long deviceTokenId = session.deviceTokenId();
        if (deviceTokenId != null) {
            jdbc.query("SELECT id FROM device_tokens WHERE id=? FOR UPDATE", rs -> null, deviceTokenId);
        }
        jdbc.update("UPDATE refresh_sessions SET revoked_at=? WHERE id=? AND user_id=? AND revoked_at IS NULL",
                Timestamp.from(now), sessionId, userId);
        jdbc.update("""
                DELETE FROM device_tokens d WHERE d.id IN
                    (SELECT device_token_id FROM refresh_sessions WHERE id=? AND user_id=? AND device_token_id IS NOT NULL)
                    AND NOT EXISTS (SELECT 1 FROM refresh_sessions r WHERE r.device_token_id=d.id
                        AND r.revoked_at IS NULL AND r.expires_at>?)
                """, session.id(), userId, Timestamp.from(now));
    }

    private String newRefreshToken() {
        byte[] bytes = new byte[48]; RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    private record Session(long id, long userId, Long deviceTokenId, Instant expiresAt, Timestamp revokedAt) { }
}
