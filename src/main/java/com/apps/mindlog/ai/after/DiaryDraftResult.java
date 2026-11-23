package com.apps.mindlog.ai.after;
import com.apps.mindlog.global.validation.VisibleLength;
import jakarta.validation.constraints.NotNull;
public record DiaryDraftResult(@NotNull @VisibleLength(min=1,max=5000) String content){}
