package com.apps.mindlog.auth.service;

import com.apps.mindlog.auth.dto.SignupRequest;
import com.apps.mindlog.auth.dto.SignupResponse;
import com.apps.mindlog.global.config.TimeConfig;
import com.apps.mindlog.global.error.ErrorType;
import com.apps.mindlog.global.error.exception.ApiException;
import com.apps.mindlog.global.idempotency.IdempotencyKeyFilter;
import com.apps.mindlog.global.security.AccessTokenCodec;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class EmailSignupService {
    private static final List<String> AGREEMENT_TYPES = List.of("terms", "privacy", "sensitive", "marketing");
    private final JdbcTemplate jdbc;
    private final AccessTokenCodec tokens;
    private final SignupSessionIssuer sessions;
    private final ObjectMapper json;
    private final Clock clock;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();

    public EmailSignupService(JdbcTemplate jdbc, AccessTokenCodec tokens, SignupSessionIssuer sessions, ObjectMapper json, Clock clock) {
        this.jdbc = jdbc; this.tokens = tokens; this.sessions = sessions; this.json = json; this.clock = clock;
    }

    @Transactional
    public String signup(SignupRequest request, HttpServletRequest servletRequest) {
        String key = IdempotencyKeyFilter.requireKey(servletRequest);
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        String nickname = request.profile().nickname().strip();
        String canonical = canonicalRequest(request, email, nickname);
        byte[] requestHash = tokens.fingerprint(canonical);
        int inserted = jdbc.update("""
                INSERT INTO signup_idempotency_records(request_key,request_hash,created_at)
                VALUES(?,?,?) ON CONFLICT (request_key) DO NOTHING
                """, key, requestHash, Timestamp.from(Instant.now(clock)));
        SignupRecord existing = jdbc.queryForObject("""
                SELECT request_hash,user_id,response_status,response_body FROM signup_idempotency_records
                WHERE request_key=? FOR UPDATE
                """, (rs, row) -> new SignupRecord(rs.getBytes(1), (Long) rs.getObject(2),
                (Integer) rs.getObject(3), rs.getBytes(4)), key);
        if (inserted == 0) {
            if (!java.security.MessageDigest.isEqual(existing.requestHash(), requestHash))
                throw new ApiException(ErrorType.IDEMPOTENCY_CONFLICT, "This idempotency key was used with a different request");
            if (existing.responseBody() == null) throw new IllegalStateException("Signup idempotency record has no result");
            return tokens.decryptResponse(existing.responseBody());
        }

        validateContract(request);
        int currentYear = LocalDate.now(clock.withZone(TimeConfig.SEOUL)).getYear();
        if (request.profile().birthYear() > currentYear || currentYear - request.profile().birthYear() < 14)
            throw validation("profile.birthYear is outside the allowed range");
        boolean activeCharacter = Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM emotion_characters WHERE id=? AND active)", Boolean.class,
                request.profile().representativeCharacterId()));
        if (!activeCharacter) throw validation("representativeCharacterId is not active");

        String passwordHash = passwords.encode(request.password());
        Instant now = Instant.now(clock);
        long userId = jdbc.queryForObject("""
                INSERT INTO users(email,password_hash,nickname,representative_character_id,gender,birth_year,
                    onboarding_completed,data_generation,account_status,after_reminder_enabled,weekly_report_start_day,created_at,updated_at)
                VALUES(?,?,?,?,?,?,false,0,'onboarding',true,'sun',?,?) RETURNING id
                """, Long.class, email, passwordHash, nickname, request.profile().representativeCharacterId(),
                request.profile().gender(), request.profile().birthYear(), Timestamp.from(now), Timestamp.from(now));

        List<SignupResponse.AgreementResponse> savedAgreements = new ArrayList<>();
        for (SignupRequest.Agreement agreement : request.agreements()) {
            jdbc.update("INSERT INTO user_agreements(user_id,agreement_type,agreement_version,agreed,agreed_at) VALUES(?,?,?,?,?)",
                    userId, agreement.agreementType(), agreement.agreementVersion(), agreement.agreed(), Timestamp.from(now));
            savedAgreements.add(new SignupResponse.AgreementResponse(agreement.agreementType(), agreement.agreementVersion(),
                    agreement.agreed(), OffsetDateTime.ofInstant(now, TimeConfig.SEOUL)));
        }
        var session = sessions.issue(userId);
        SignupResponse response = new SignupResponse(userId, session.accessToken(), session.refreshToken(), session.expiresIn(), false,
                List.copyOf(savedAgreements));
        String responseJson = writeJson(response);
        jdbc.update("""
                UPDATE signup_idempotency_records SET user_id=?,response_status=201,response_body=?,response_headers=?::jsonb
                WHERE request_key=?
                """, userId, tokens.encryptResponse(responseJson), "{\"Location\":\"/api/users/me\"}", key);
        return responseJson;
    }

    private void validateContract(SignupRequest request) {
        if (!"email".equals(request.signupType()) || request.signupToken() != null)
            throw validation("This endpoint currently accepts email signup requests only");
        if (request.deviceToken() != null)
            throw validation("deviceToken registration requires a platform and is not supported by email signup yet");
        Map<String, SignupRequest.Agreement> byType = new HashMap<>();
        for (SignupRequest.Agreement agreement : request.agreements()) {
            if (!AGREEMENT_TYPES.contains(agreement.agreementType()) || byType.put(agreement.agreementType(), agreement) != null)
                throw validation("Each agreement type must appear exactly once");
            if (agreement.agreementVersion().isBlank()) throw validation("agreementVersion must not be blank");
        }
        if (!byType.keySet().equals(new java.util.HashSet<>(AGREEMENT_TYPES))) throw validation("All four agreement types are required");
        for (String required : AGREEMENT_TYPES.subList(0, 3))
            if (!Boolean.TRUE.equals(byType.get(required).agreed())) throw validation("Required agreements must be accepted");
    }

    private String canonicalRequest(SignupRequest request, String email, String nickname) {
        Map<String, Object> canonical = new java.util.LinkedHashMap<>();
        canonical.put("signupType", request.signupType()); canonical.put("email", email); canonical.put("password", request.password());
        canonical.put("signupToken", request.signupToken()); canonical.put("agreements", request.agreements());
        canonical.put("nickname", nickname); canonical.put("representativeCharacterId", request.profile().representativeCharacterId());
        canonical.put("gender", request.profile().gender()); canonical.put("birthYear", request.profile().birthYear());
        canonical.put("deviceToken", request.deviceToken());
        return writeJson(canonical);
    }

    private String writeJson(Object value) {
        try { return json.writeValueAsString(value); }
        catch (tools.jackson.core.JacksonException ex) { throw new IllegalStateException("Unable to serialize signup data", ex); }
    }
    private static ApiException validation(String message) { return new ApiException(ErrorType.VALIDATION_ERROR, message); }

    private record SignupRecord(byte[] requestHash, Long userId, Integer responseStatus, byte[] responseBody) { }
}
