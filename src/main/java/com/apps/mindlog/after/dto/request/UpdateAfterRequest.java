package com.apps.mindlog.after.dto.request;

import com.apps.mindlog.global.validation.VisibleLength;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.*;
import java.util.List;
import tools.jackson.databind.JsonNode;

public class UpdateAfterRequest {
    @NotNull @Positive private Long inputVersion;
    @VisibleLength(min=1,max=300) private String freeWriting;
    @Size(min=1,max=8) private List<@NotNull @Positive Long> bodySymptomIds;
    public Long getInputVersion(){return inputVersion;}public void setInputVersion(JsonNode value){inputVersion=AfterJsonInput.integer(value);}
    public String getFreeWriting(){return freeWriting;}
    public void setFreeWriting(String value){if(value==null)throw new IllegalArgumentException("Null writing");freeWriting=value;}
    public List<Long> getBodySymptomIds(){return bodySymptomIds;}
    public void setBodySymptomIds(JsonNode value){bodySymptomIds=AfterJsonInput.integers(value);}
    @JsonAnySetter public void rejectUnknown(String field,Object value){throw new IllegalArgumentException("Unsupported request field");}
}
