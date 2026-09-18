package com.apps.mindlog.global.error.exception;

import com.apps.mindlog.global.error.ErrorType;
import java.util.Objects;

/** detail에는 클라이언트에 공개 가능한 설명만 전달한다. */
public class ApiException extends RuntimeException {
    private final ErrorType errorType;

    public ApiException(ErrorType errorType) {
        this(errorType, errorType.getTitle());
    }

    public ApiException(ErrorType errorType, String detail) {
        super(Objects.requireNonNull(detail));
        this.errorType = Objects.requireNonNull(errorType);
    }

    public ErrorType getErrorType() { return errorType; }
}
