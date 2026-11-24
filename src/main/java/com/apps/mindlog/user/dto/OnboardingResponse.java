package com.apps.mindlog.user.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record OnboardingResponse(String currentEmotionCode, Integer baselineDiscomfortScore,
        List<Selection> situationTypes, List<Selection> bodySymptoms,
        boolean onboardingCompleted, OffsetDateTime onboardingCompletedAt) {
    public record Selection(Long id, String name) { }
}
