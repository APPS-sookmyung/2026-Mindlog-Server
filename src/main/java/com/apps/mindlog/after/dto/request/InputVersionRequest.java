package com.apps.mindlog.after.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.*;
import tools.jackson.databind.JsonNode;

public class InputVersionRequest {
    @NotNull @Positive private Long inputVersion;
    public Long getInputVersion(){return inputVersion;}
    public void setInputVersion(JsonNode value){
        if(value==null||!value.isIntegralNumber()||!value.canConvertToLong())throw new IllegalArgumentException("Integer version required");
        inputVersion=value.longValue();
    }
    @JsonAnySetter public void rejectUnknown(String field,Object value){throw new IllegalArgumentException("Unsupported request field");}
}
