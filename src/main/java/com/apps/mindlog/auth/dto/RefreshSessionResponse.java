package com.apps.mindlog.auth.dto;

public record RefreshSessionResponse(String accessToken, String refreshToken, long expiresIn) { }
