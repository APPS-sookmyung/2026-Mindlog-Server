package com.apps.mindlog.global.error;

import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.hibernate.exception.ConstraintViolationException;
import org.postgresql.util.PSQLException;

/** Maps only documented unique conflicts; never extracts names from SQL/error text. */
public final class ConstraintViolationMapper {
    private static final String UNIQUE_VIOLATION = "23505";
    private static final Map<String, Conflict> CONFLICTS = Map.of(
            "ux_users_email_lower", new Conflict(ErrorType.EMAIL_ALREADY_EXISTS, "이미 가입된 이메일입니다."),
            "ux_after_logs_before_log_id", new Conflict(ErrorType.AFTER_LOG_ALREADY_EXISTS, "이미 After 기록이 존재합니다."),
            "ux_notifications_user_dedup", new Conflict(ErrorType.NOTIFICATION_ALREADY_SCHEDULED, "이미 등록된 알림입니다."),
            "ux_ai_jobs_active_after_kind", new Conflict(ErrorType.ANALYSIS_IN_PROGRESS, "이미 진행 중인 작업이 있습니다."));

    private ConstraintViolationMapper() { }

    public static Optional<Conflict> map(Throwable failure) {
        if (failure == null) return Optional.empty();
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        var pending = new ArrayDeque<Throwable>();
        pending.add(failure);
        while (!pending.isEmpty()) {
            Throwable current = pending.removeFirst();
            if (!visited.add(current)) continue;
            String name = null;
            if (current instanceof ConstraintViolationException hibernate
                    && UNIQUE_VIOLATION.equals(hibernate.getSQLState())) {
                name = hibernate.getConstraintName();
            } else if (current instanceof PSQLException postgres
                    && UNIQUE_VIOLATION.equals(postgres.getSQLState())
                    && postgres.getServerErrorMessage() != null) {
                name = postgres.getServerErrorMessage().getConstraint();
            }
            if (name != null && CONFLICTS.containsKey(name)) {
                return Optional.of(CONFLICTS.get(name));
            }
            if (current.getCause() != null) pending.addLast(current.getCause());
            if (current instanceof SQLException sql && sql.getNextException() != null) {
                pending.addLast(sql.getNextException());
            }
        }
        return Optional.empty();
    }

    public record Conflict(ErrorType errorType, String detail) { }
}
