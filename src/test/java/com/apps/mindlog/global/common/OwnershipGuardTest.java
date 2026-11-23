package com.apps.mindlog.global.common;

import com.apps.mindlog.global.error.ErrorType;
import com.apps.mindlog.global.error.exception.ApiException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.*;

class OwnershipGuardTest {
    record Resource(Long ownerId) { }

    @Test
    void comparesLongValuesOutsideTheBoxingCache() {
        assertThatCode(() -> OwnershipGuard.requireOwner(Long.valueOf("10000"), Long.valueOf("10000")))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource(value = {"1,2", "null,1", "1,null", "null,null"}, nullValues = "null")
    void hidesForeignAndMissingOwnership(Long userId, Long ownerId) {
        assertThatThrownBy(() -> OwnershipGuard.requireOwner(userId, ownerId))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getErrorType()).isEqualTo(ErrorType.RESOURCE_NOT_FOUND);
                    assertThat(exception.getMessage()).isEqualTo("요청한 리소스를 찾을 수 없습니다.");
                });
    }

    @Test
    void returnsTheSameOwnedResource() {
        var resource = new Resource(42L);
        assertThat(OwnershipGuard.requireOwned(Optional.of(resource), 42L, Resource::ownerId)).isSameAs(resource);
    }

    @Test
    void missingAndForeignResourcesHaveIdenticalErrors() {
        ApiException absent = catchThrowableOfType(ApiException.class,
                () -> OwnershipGuard.requireOwned(Optional.<Resource>empty(), 1L, Resource::ownerId));
        ApiException foreign = catchThrowableOfType(ApiException.class,
                () -> OwnershipGuard.requireOwned(Optional.of(new Resource(2L)), 1L, Resource::ownerId));
        assertThat(absent.getErrorType()).isEqualTo(foreign.getErrorType()).isEqualTo(ErrorType.RESOURCE_NOT_FOUND);
        assertThat(absent.getMessage()).isEqualTo(foreign.getMessage());
    }
}
