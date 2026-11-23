package com.apps.mindlog.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EmailLoginRequest(@NotBlank @Size(max = 300) String email,
                                @NotBlank String password,
                                @Size(min = 1) @Pattern(regexp = ".*\\S.*") String deviceToken) { }
