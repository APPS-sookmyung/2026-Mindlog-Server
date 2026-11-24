package com.apps.mindlog.auth.service;

import com.apps.mindlog.auth.controller.EmailAvailabilityController.EmailAvailabilityResponse;
import com.apps.mindlog.global.error.ErrorType;
import com.apps.mindlog.global.error.exception.ApiException;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.servlet.http.HttpServletRequest;

@Service
public class EmailAvailabilityService {
    private final UserEmailLookup userEmailLookup;
    private final EmailAvailabilityRateLimiter rateLimiter;
    private final Validator validator;

    public EmailAvailabilityService(UserEmailLookup userEmailLookup, EmailAvailabilityRateLimiter rateLimiter,
            Validator validator) {
        this.userEmailLookup = userEmailLookup;
        this.rateLimiter = rateLimiter;
        this.validator = validator;
    }

    @Transactional
    public EmailAvailabilityResponse check(String email, HttpServletRequest request) {
        String normalized = email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
        var violations = validator.validate(new EmailQuery(normalized));
        if (!violations.isEmpty()) {
            List<Map<String,String>> errors = violations.stream()
                    .map(violation -> Map.of("field", "email", "reason", switch (violation.getMessageTemplate()) {
                        case "{jakarta.validation.constraints.NotBlank.message}" -> "required";
                        case "{jakarta.validation.constraints.Email.message}" -> "format";
                        case "{jakarta.validation.constraints.Size.message}" -> "size";
                        default -> "invalid";
                    }))
                    .distinct()
                    .sorted(java.util.Comparator.comparing(error -> error.get("reason")))
                    .toList();
            throw new ApiException(ErrorType.VALIDATION_ERROR, "이메일을 확인해 주세요.", Map.of("errors", errors));
        }
        rateLimiter.checkAllowed(request.getRemoteAddr());
        try {
            return new EmailAvailabilityResponse(normalized, !userEmailLookup.existsByEmailIgnoreCase(normalized));
        } catch (DataAccessException exception) {
            throw new ApiException(ErrorType.INTERNAL_ERROR, "요청을 처리하는 중 오류가 발생했습니다.");
        }
    }

    private record EmailQuery(@NotBlank @Email @Size(max = 254) String email) { }
}
