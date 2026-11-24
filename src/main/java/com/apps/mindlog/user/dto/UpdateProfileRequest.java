package com.apps.mindlog.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(min = 1, max = 20) @Pattern(regexp = "[^\\r\\n]+") String nickname,
        Long representativeCharacterId) { }
