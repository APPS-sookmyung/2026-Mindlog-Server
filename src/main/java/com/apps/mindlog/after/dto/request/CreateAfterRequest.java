package com.apps.mindlog.after.dto.request;

import com.apps.mindlog.global.validation.VisibleLength;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.*;
import java.util.List;

public record CreateAfterRequest(@NotNull @Positive Long beforeLogId,
        @NotNull @VisibleLength(min=1,max=300) String freeWriting,
        @NotNull @Size(min=1,max=8) List<@NotNull @Positive Long> bodySymptomIds){
    @JsonAnySetter public void rejectUnknown(String field,Object value){throw new IllegalArgumentException("Unsupported request field");}
}
