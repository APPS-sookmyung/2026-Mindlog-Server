package com.apps.mindlog.after.dto.response;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record AnalysisResult(@NotBlank String summary,@Min(0) int patternCount,
        @NotNull List<@Valid Distortion> distortions,@NotEmpty List<@Valid Card> analysisCards){
    public AnalysisResult{distortions=List.copyOf(distortions);analysisCards=List.copyOf(analysisCards);}
    public record Distortion(@Positive long distortionTagId,@NotBlank String distortionTagName,@NotBlank String evidence,@NotBlank String explanation){}
    public record Card(@NotBlank String tagLabel,@Positive Long distortionTagId,@NotBlank String title,@NotBlank String content){}
}
