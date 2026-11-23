package com.apps.mindlog.before.dto.response;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record BeforeCreatedResponse(Long id, Long situationTypeId, String situationTypeName,
        LocalDate scheduledAt, Integer expectedScore, String worstScenario, String title,
        String description, boolean hasAfter, Long afterLogId, OffsetDateTime createdAt) { }
