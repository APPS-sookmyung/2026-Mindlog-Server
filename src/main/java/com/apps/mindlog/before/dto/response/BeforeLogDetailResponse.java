package com.apps.mindlog.before.dto.response;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record BeforeLogDetailResponse(
        Long id,
        Long situationTypeId,
        String situationTypeName,
        LocalDate scheduledAt,
        Integer expectedScore,
        String worstScenario,
        Boolean hasAfter,
        Long afterLogId,
        String title,
        String description,
        Rebuttal rebuttal,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public record Rebuttal(String feedbackContent) { }
}
