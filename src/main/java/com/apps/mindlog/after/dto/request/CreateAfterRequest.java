package com.apps.mindlog.after.dto.request;

import com.apps.mindlog.global.validation.VisibleLength;
import com.fasterxml.jackson.annotation.*;
import jakarta.validation.constraints.*;
import java.util.List;
import tools.jackson.databind.JsonNode;

public record CreateAfterRequest(@NotNull @Positive Long beforeLogId,
        @NotNull @VisibleLength(min=1,max=300) String freeWriting,
        @NotNull @Size(min=1,max=8) List<@NotNull @Positive Long> bodySymptomIds){
    @JsonCreator public static CreateAfterRequest fromJson(@JsonProperty("beforeLogId") JsonNode beforeLogId,
            @JsonProperty("freeWriting") String freeWriting,@JsonProperty("bodySymptomIds") JsonNode bodySymptomIds){
        return new CreateAfterRequest(AfterJsonInput.integer(beforeLogId),freeWriting,AfterJsonInput.integers(bodySymptomIds));
    }
    @JsonAnySetter public void rejectUnknown(String field,Object value){throw new IllegalArgumentException("Unsupported request field");}
}
