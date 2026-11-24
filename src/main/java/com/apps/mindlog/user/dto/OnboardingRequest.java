package com.apps.mindlog.user.dto;

import java.util.List;

public record OnboardingRequest(String currentEmotionCode, Integer baselineDiscomfortScore,
        List<Long> situationTypeIds, List<Long> bodySymptomIds) { }
