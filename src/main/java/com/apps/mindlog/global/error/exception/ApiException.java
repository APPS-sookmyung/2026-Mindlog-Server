package com.apps.mindlog.global.error.exception;

import com.apps.mindlog.global.error.ErrorType;
import java.util.Objects;
import java.util.OptionalLong;

/** detail에는 클라이언트에 공개 가능한 설명만 전달한다. */
public class ApiException extends RuntimeException {
    private final ErrorType errorType;
    private final Long retryAfterSeconds;

    public ApiException(ErrorType errorType) { this(errorType, errorType.getTitle()); }

    public ApiException(ErrorType errorType, String detail) { this(errorType, detail, (Long) null); }

    public ApiException(ErrorType errorType, String detail, long retryAfterSeconds) {
        this(errorType, detail, Long.valueOf(retryAfterSeconds));
    }

    private ApiException(ErrorType errorType, String detail, Long retryAfterSeconds) {
        super(Objects.requireNonNull(detail));
        this.errorType = Objects.requireNonNull(errorType);
        if (errorType == ErrorType.LOGIN_RATE_LIMITED && retryAfterSeconds == null)
            throw new IllegalArgumentException("Login rate limit requires Retry-After seconds");
        if (retryAfterSeconds != null && (retryAfterSeconds < 0 || errorType.getStatus().value() != 429))
            throw new IllegalArgumentException("Retry-After requires a 429 error and nonnegative seconds");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public ErrorType getErrorType() { return errorType; }
    public OptionalLong getRetryAfterSeconds() {
        return retryAfterSeconds == null ? OptionalLong.empty() : OptionalLong.of(retryAfterSeconds);
    }
}
