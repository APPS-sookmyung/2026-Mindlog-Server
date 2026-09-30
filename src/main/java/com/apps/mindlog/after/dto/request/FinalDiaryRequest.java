package com.apps.mindlog.after.dto.request;

import com.apps.mindlog.global.validation.VisibleLength;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.*;
import tools.jackson.databind.JsonNode;

public class FinalDiaryRequest {
    @NotNull @Positive private Long inputVersion;
    @NotNull @VisibleLength(min=1,max=5000) private String finalDiary;
    @NotNull @Min(0) @Max(100) private Integer actualScore;
    public Long getInputVersion(){return inputVersion;}
    public void setInputVersion(JsonNode value){
        if(value==null||!value.isIntegralNumber()||!value.canConvertToLong())throw new IllegalArgumentException("Integer version required");
        inputVersion=value.longValue();
    }
    public String getFinalDiary(){return finalDiary;}public void setFinalDiary(String value){finalDiary=value;}
    public Integer getActualScore(){return actualScore;}
    public void setActualScore(JsonNode value){
        if(value==null||!value.isIntegralNumber()||!value.canConvertToInt())throw new IllegalArgumentException("Integer score required");
        actualScore=value.intValue();
    }
    @JsonAnySetter public void rejectUnknown(String field,Object value){throw new IllegalArgumentException("Unsupported request field");}
}
