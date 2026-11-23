package com.apps.mindlog.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshSessionRequest(@NotBlank String refreshToken) { }
