package com.apps.mindlog.global.idempotency;

import com.apps.mindlog.global.error.ErrorType;
import com.apps.mindlog.global.error.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class IdempotencyService {
    private static final ObjectMapper STRICT_JSON = tools.jackson.databind.json.JsonMapper.builder()
            .enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(tools.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(tools.jackson.databind.DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
            .build();
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ObjectMapper json;
    private final Clock clock;

    public IdempotencyService(JdbcTemplate jdbc, PlatformTransactionManager manager, ObjectMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(manager);
        this.json = json;
        this.clock = clock;
    }

    /**
     * accessCheck must lock/check current ownership, generation and account status inside this transaction.
     * It runs before replay too. action must use the same DB transaction and must not call remote services.
     */
    public IdempotencyRecord execute(Scope scope, String key, String requestJson,
            Runnable accessCheck, Supplier<IdempotencyRecord> action) {
        Objects.requireNonNull(scope);
        Objects.requireNonNull(accessCheck);
        Objects.requireNonNull(action);
        validateKey(key);
        byte[] hash = hash(requestJson);
        return transactions.execute(transaction -> {
            accessCheck.run();
            jdbc.update("""
                    INSERT INTO idempotency_records(user_id,data_generation,operation,request_key,request_hash,created_at)
                    VALUES(?,?,?,?,?,?) ON CONFLICT DO NOTHING
                    """, scope.userId(), scope.dataGeneration(), scope.operation(), key, hash, Timestamp.from(Instant.now(clock)));
            var saved = jdbc.queryForObject("""
                    SELECT request_hash,response_status,response_body,response_headers::text
                    FROM idempotency_records WHERE user_id=? AND data_generation=? AND operation=? AND request_key=?
                    FOR UPDATE
                    """, (row, index) -> new Saved(row.getBytes(1), (Integer) row.getObject(2), row.getString(3), row.getString(4)),
                    scope.userId(), scope.dataGeneration(), scope.operation(), key);
            if (!MessageDigest.isEqual(saved.hash(), hash)) {
                throw new ApiException(ErrorType.IDEMPOTENCY_CONFLICT, "같은 요청 키에 다른 입력을 사용할 수 없습니다.");
            }
            if (saved.status() != null) {
                return new IdempotencyRecord(saved.status(), saved.body(),
                        json.readValue(saved.headers(), new TypeReference<Map<String, String>>() { }));
            }
            IdempotencyRecord result = Objects.requireNonNull(action.get());
            jdbc.update("""
                    UPDATE idempotency_records SET response_status=?,response_body=?,response_headers=CAST(? AS jsonb)
                    WHERE user_id=? AND data_generation=? AND operation=? AND request_key=?
                    """, result.status(), result.body(), json.writeValueAsString(result.headers()),
                    scope.userId(), scope.dataGeneration(), scope.operation(), key);
            return result;
        });
    }

    /** Called by the account-reset service in its transaction while holding the account lock. */
    public void deleteForUser(long userId) {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Account reset requires a transaction");
        }
        jdbc.update("DELETE FROM idempotency_records WHERE user_id=?", userId);
    }

    static void validateKey(String key) {
        if (key == null || key.isEmpty() || key.length() > 255
                || key.chars().anyMatch(c -> c < 33 || c > 126)) {
            throw new ApiException(ErrorType.VALIDATION_ERROR, "Idempotency-Key 헤더를 확인해 주세요.");
        }
    }

    private byte[] hash(String input) {
        if (input == null) throw new ApiException(ErrorType.VALIDATION_ERROR, "요청 JSON을 확인해 주세요.");
        try {
            Object value = STRICT_JSON.readValue(input, Object.class);
            return MessageDigest.getInstance("SHA-256").digest(
                    json.writeValueAsString(canonical(value)).getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        } catch (tools.jackson.core.JacksonException | IllegalArgumentException exception) {
            throw new ApiException(ErrorType.VALIDATION_ERROR, "요청 JSON을 확인해 주세요.");
        }
    }

    private Object canonical(Object value) {
        if (value instanceof Map<?, ?> object) {
            var ordered = new TreeMap<String, Object>();
            object.forEach((key, child) -> ordered.put((String) key, canonical(child)));
            return ordered;
        }
        if (value instanceof List<?> array) {
            var ordered = new ArrayList<Object>(array.size());
            array.forEach(child -> ordered.add(canonical(child)));
            return ordered;
        }
        return value;
    }

    public record Scope(long userId, long dataGeneration, String operation) {
        public Scope {
            if (userId <= 0 || dataGeneration < 0 || operation == null || operation.isBlank() || operation.length() > 255) {
                throw new IllegalArgumentException("Invalid server-provided idempotency scope");
            }
        }
    }

    private record Saved(byte[] hash, Integer status, String body, String headers) { }
}
