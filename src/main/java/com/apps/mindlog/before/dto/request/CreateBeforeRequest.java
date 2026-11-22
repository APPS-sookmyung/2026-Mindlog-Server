package com.apps.mindlog.before.dto.request;

import com.apps.mindlog.global.validation.VisibleLength;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record CreateBeforeRequest(
        @NotNull @Positive Long situationTypeId,
        @NotNull LocalDate scheduledAt,
        @NotNull @Min(0) @Max(100) Integer expectedScore,
        @NotNull @VisibleLength(min = 1, max = 300) String worstScenario,
        @Size(max = 300) String title,
        @Size(max = 1000) String description,
        @NotNull UUID rebuttalJobId,
        @NotNull @Positive Long inputVersion) {
    @JsonCreator public CreateBeforeRequest { }
    @JsonAnySetter public void rejectUnknown(String field, Object value) {
        throw new IllegalArgumentException("Unsupported request field");
    }
}
