package com.apps.mindlog.auth.service;

import com.apps.mindlog.auth.dto.EmailLoginRequest;
import com.apps.mindlog.auth.dto.EmailLoginResponse;
import com.apps.mindlog.global.error.ErrorType;
import com.apps.mindlog.global.error.exception.ApiException;
import com.apps.mindlog.global.security.AccessTokenCodec;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailLoginService {
    private static final long WINDOW_SECONDS = 900;
    private static final int MAX_FAILURES = 5;
    private static final String DUMMY_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p4Zk5W6MiZ4x3FJ0bGfR8a";
    private final JdbcTemplate jdbc;
    private final AccessTokenCodec tokens;
    private final SignupSessionIssuer sessions;
    private final Clock clock;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();

    public EmailLoginService(JdbcTemplate jdbc, AccessTokenCodec tokens, SignupSessionIssuer sessions, Clock clock) {
        this.jdbc = jdbc; this.tokens = tokens; this.sessions = sessions; this.clock = clock;
    }

    @Transactional(noRollbackFor = ApiException.class)
    public EmailLoginResponse login(EmailLoginRequest request) {
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        if (email.isEmpty() || email.length() > 254 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
            throw new ApiException(ErrorType.VALIDATION_ERROR, "email 형식을 확인해 주세요.");
        Instant now = Instant.now(clock);
        byte[] key = tokens.fingerprint("email-login:" + email);
        jdbc.update("""
                INSERT INTO email_login_attempts(email_key,failed_login_count,updated_at) VALUES(?,0,?)
                ON CONFLICT(email_key) DO NOTHING
                """, key, Timestamp.from(now));
        Attempt attempt = jdbc.queryForObject("""
                SELECT failed_login_count,failed_login_window_started_at,login_locked_until
                FROM email_login_attempts WHERE email_key=? FOR UPDATE
                """, (rs, n) -> new Attempt(rs.getInt(1), rs.getTimestamp(2), rs.getTimestamp(3)), key);
        if (attempt.lockedUntil() != null && attempt.lockedUntil().toInstant().isAfter(now))
            throw new ApiException(ErrorType.LOGIN_RATE_LIMITED, "로그인 시도가 많습니다.",
                    Math.max(1, attempt.lockedUntil().toInstant().getEpochSecond() - now.getEpochSecond()));

        User user = jdbc.query("""
                SELECT id,password_hash,onboarding_completed,account_status
                FROM users WHERE lower(email)=? AND account_status IN ('active','onboarding') LIMIT 1 FOR UPDATE
                """, rs -> rs.next() ? new User(rs.getLong(1), rs.getString(2), rs.getBoolean(3), rs.getString(4)) : null, email);
        boolean valid = user != null && user.passwordHash() != null
                && passwords.matches(request.password(), user.passwordHash());
        if (user == null || user.passwordHash() == null) passwords.matches(request.password(), DUMMY_HASH);
        if (!valid) {
            recordFailure(key, attempt, now, email, user);
            throw new ApiException(ErrorType.INVALID_CREDENTIALS, "이메일 또는 비밀번호를 확인해 주세요.");
        }

        jdbc.update("UPDATE email_login_attempts SET failed_login_count=0,failed_login_window_started_at=NULL,login_locked_until=NULL,updated_at=? WHERE email_key=?",
                Timestamp.from(now), key);
        jdbc.update("UPDATE users SET failed_login_count=0,failed_login_window_started_at=NULL,login_locked_until=NULL,updated_at=? WHERE id=?",
                Timestamp.from(now), user.id());
        var issued = sessions.issue(user.id(), request.deviceToken());
        return new EmailLoginResponse(issued.accessToken(), issued.refreshToken(), issued.expiresIn(), user.onboardingCompleted());
    }

    private void recordFailure(byte[] key, Attempt attempt, Instant now, String email, User user) {
        Instant started = attempt.windowStarted() == null ? now : attempt.windowStarted().toInstant();
        boolean inWindow = started.plusSeconds(WINDOW_SECONDS).isAfter(now);
        int count = inWindow ? attempt.failures() + 1 : 1;
        Instant window = count == 1 ? now : started;
        Instant lock = count == MAX_FAILURES || (inWindow && attempt.lockedUntil() != null)
                ? now.plusSeconds(WINDOW_SECONDS) : null;
        jdbc.update("""
                UPDATE email_login_attempts SET failed_login_count=?,failed_login_window_started_at=?,
                    login_locked_until=?,updated_at=? WHERE email_key=?
                """, count, Timestamp.from(window), lock == null ? null : Timestamp.from(lock), Timestamp.from(now), key);
        if (user != null) jdbc.update("UPDATE users SET failed_login_count=?,failed_login_window_started_at=?,login_locked_until=?,updated_at=? WHERE id=?",
                count, Timestamp.from(window), lock == null ? null : Timestamp.from(lock), Timestamp.from(now), user.id());
    }

    private record Attempt(int failures, Timestamp windowStarted, Timestamp lockedUntil) { }
    private record User(long id, String passwordHash, boolean onboardingCompleted, String accountStatus) { }
}
