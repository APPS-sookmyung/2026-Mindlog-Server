package com.apps.mindlog.after.dto.request;

import com.apps.mindlog.global.validation.VisibleLength;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.*;
import java.util.List;

public class UpdateAfterRequest {
    @NotNull @Positive private Long inputVersion;
    @VisibleLength(min=1,max=300) private String freeWriting;
    @Size(min=1,max=8) private List<@NotNull @Positive Long> bodySymptomIds;
    public Long getInputVersion(){return inputVersion;}public void setInputVersion(Long value){inputVersion=value;}
    public String getFreeWriting(){return freeWriting;}
    public void setFreeWriting(String value){if(value==null)throw new IllegalArgumentException("Null writing");freeWriting=value;}
    public List<Long> getBodySymptomIds(){return bodySymptomIds;}
    public void setBodySymptomIds(List<Long> value){if(value==null)throw new IllegalArgumentException("Null symptoms");bodySymptomIds=value;}
    @JsonAnySetter public void rejectUnknown(String field,Object value){throw new IllegalArgumentException("Unsupported request field");}
}
