package com.apps.mindlog.user.dto;

import jakarta.validation.constraints.Pattern;
import com.apps.mindlog.global.validation.VisibleLength;

public record UpdateProfileRequest(
        @VisibleLength(min = 1, max = 20) @Pattern(regexp = "[^\\r\\n]+") String nickname,
        Long representativeCharacterId) { }
