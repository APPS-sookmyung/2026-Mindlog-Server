package com.apps.mindlog.before.dto.response;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record BeforeLogListResponse(List<Item> content, PageInfo page) {
    public record Item(Long id, Long situationTypeId, String situationTypeName, LocalDate scheduledAt,
            Integer expectedScore, String worstScenario, boolean hasAfter, Long afterLogId,
            OffsetDateTime createdAt) { }

    public record PageInfo(int size, int number, long totalElements, int totalPages) { }
}
