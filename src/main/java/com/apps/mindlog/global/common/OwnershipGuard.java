package com.apps.mindlog.global.common;

import com.apps.mindlog.global.error.ErrorType;
import com.apps.mindlog.global.error.exception.ApiException;
import java.util.Optional;
import java.util.function.Function;

/** For path resources only. Request-body references are validated separately as 400. */
public final class OwnershipGuard {
    private static final String NOT_FOUND = "요청한 리소스를 찾을 수 없습니다.";

    private OwnershipGuard() { }

    public static void requireOwner(Long currentUserId, Long ownerId) {
        if (currentUserId == null || ownerId == null || !currentUserId.equals(ownerId)) {
            throw notFound();
        }
    }

    public static <T> T requireOwned(Optional<T> resource, Long currentUserId, Function<T, Long> ownerId) {
        T value = resource.orElseThrow(OwnershipGuard::notFound);
        requireOwner(currentUserId, ownerId.apply(value));
        return value;
    }

    private static ApiException notFound() {
        return new ApiException(ErrorType.RESOURCE_NOT_FOUND, NOT_FOUND);
    }
}
