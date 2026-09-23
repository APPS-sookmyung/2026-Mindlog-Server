package com.apps.mindlog.global.error;

import java.net.URI;
import org.springframework.http.HttpStatus;

public enum ErrorType {
    VALIDATION_ERROR("validation-error", HttpStatus.BAD_REQUEST, "Validation Failed"),
    INVALID_ID_TOKEN("invalid-id-token", HttpStatus.UNAUTHORIZED, "Invalid ID Token"),
    EXPIRED_ID_TOKEN("expired-id-token", HttpStatus.UNAUTHORIZED, "Expired ID Token"),
    INVALID_REFRESH_TOKEN("invalid-refresh-token", HttpStatus.UNAUTHORIZED, "Invalid Refresh Token"),
    EXPIRED_REFRESH_TOKEN("expired-refresh-token", HttpStatus.UNAUTHORIZED, "Expired Refresh Token"),
    INVALID_TOKEN("invalid-token", HttpStatus.UNAUTHORIZED, "Invalid Token"),
    EXPIRED_TOKEN("expired-token", HttpStatus.UNAUTHORIZED, "Expired Token"),
    RESOURCE_NOT_FOUND("resource-not-found", HttpStatus.NOT_FOUND, "Resource Not Found"),
    AFTER_LOG_ALREADY_EXISTS("after-log-already-exists", HttpStatus.CONFLICT, "After Log Already Exists"),
    ANALYSIS_IN_PROGRESS("analysis-in-progress", HttpStatus.CONFLICT, "Analysis In Progress"),
    FINAL_DIARY_ALREADY_CONFIRMED("final-diary-already-confirmed", HttpStatus.CONFLICT, "Final Diary Already Confirmed"),
    AFTER_LOG_FINALIZED("after-log-finalized", HttpStatus.CONFLICT, "After Log Finalized"),
    NOTIFICATION_NOT_ACTIONABLE("notification-not-actionable", HttpStatus.CONFLICT, "Notification Not Actionable"),
    NOTIFICATION_ALREADY_SCHEDULED("notification-already-scheduled", HttpStatus.CONFLICT, "Notification Already Scheduled"),
    IDEMPOTENCY_CONFLICT("idempotency-conflict", HttpStatus.CONFLICT, "Idempotency Conflict"),
    STALE_INPUT_VERSION("stale-input-version", HttpStatus.CONFLICT, "Stale Input Version"),
    ANALYSIS_NOT_READY("analysis-not-ready", HttpStatus.CONFLICT, "Analysis Not Ready"),
    AI_ANALYSIS_FAILED("ai-analysis-failed", HttpStatus.UNPROCESSABLE_CONTENT, "AI Analysis Failed"),
    RATE_LIMITED("rate-limited", HttpStatus.TOO_MANY_REQUESTS, "Rate Limited"),
    INTERNAL_ERROR("internal-error", HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error");

    private final URI type;
    private final HttpStatus status;
    private final String title;

    ErrorType(String code, HttpStatus status, String title) {
        this.type = URI.create("https://api.mindlog.com/problems/" + code);
        this.status = status;
        this.title = title;
    }

    public URI getType() { return type; }
    public HttpStatus getStatus() { return status; }
    public String getTitle() { return title; }
}
