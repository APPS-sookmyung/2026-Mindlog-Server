package com.apps.mindlog.auth.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record SignupResponse(long userId, String accessToken, String refreshToken, long expiresIn,
        boolean onboardingCompleted, List<AgreementResponse> agreements) {
    public record AgreementResponse(String agreementType, String agreementVersion, boolean agreed, OffsetDateTime agreedAt) { }
}
