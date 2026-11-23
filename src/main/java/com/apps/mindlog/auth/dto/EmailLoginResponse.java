package com.apps.mindlog.auth.dto;

public record EmailLoginResponse(String accessToken, String refreshToken, long expiresIn,
                                 boolean onboardingCompleted) { }
