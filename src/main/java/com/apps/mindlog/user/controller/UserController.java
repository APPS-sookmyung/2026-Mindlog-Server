package com.apps.mindlog.user.controller;

import com.apps.mindlog.user.dto.UpdateProfileRequest;
import com.apps.mindlog.user.dto.UserProfileResponse;
import com.apps.mindlog.user.dto.OnboardingRequest;
import com.apps.mindlog.user.dto.OnboardingResponse;
import com.apps.mindlog.user.service.OnboardingService;
import com.apps.mindlog.user.service.UserProfileService;
import jakarta.validation.Validator;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/users/me", version = "1")
public class UserController {
    private final UserProfileService profiles;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final OnboardingService onboarding;

    public UserController(UserProfileService profiles, ObjectMapper objectMapper, Validator validator,
            OnboardingService onboarding) {
        this.profiles = profiles;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.onboarding = onboarding;
    }

    @GetMapping
    public UserProfileResponse me() { return profiles.me(); }

    @PutMapping("/onboarding")
    public OnboardingResponse completeOnboarding(@RequestBody JsonNode body) {
        return onboarding.complete(parseOnboardingRequest(body));
    }

    private OnboardingRequest parseOnboardingRequest(JsonNode body) {
        if (body == null || !body.isObject()) throw invalidOnboarding();
        if (!body.has("currentEmotionCode") && !body.has("baselineDiscomfortScore")
                && !body.has("situationTypeIds") && !body.has("bodySymptomIds")) return null;
        if (!body.has("currentEmotionCode") || !body.get("currentEmotionCode").isTextual()
                || !body.has("baselineDiscomfortScore") || !body.get("baselineDiscomfortScore").isIntegralNumber()
                || !body.get("baselineDiscomfortScore").canConvertToInt()
                || !body.has("situationTypeIds") || !body.get("situationTypeIds").isArray()
                || !body.has("bodySymptomIds") || !body.get("bodySymptomIds").isArray()) throw invalidOnboarding();
        for (var id : body.get("situationTypeIds"))
            if (!id.isIntegralNumber() || !id.canConvertToLong()) throw invalidOnboarding();
        for (var id : body.get("bodySymptomIds"))
            if (!id.isIntegralNumber() || !id.canConvertToLong()) throw invalidOnboarding();
        return objectMapper.treeToValue(body, OnboardingRequest.class);
    }

    private static com.apps.mindlog.global.error.exception.ApiException invalidOnboarding() {
        return new com.apps.mindlog.global.error.exception.ApiException(
                com.apps.mindlog.global.error.ErrorType.VALIDATION_ERROR, "Invalid onboarding fields");
    }

    @PatchMapping("/profile")
    public UserProfileService.ProfileUpdateResponse updateProfile(@RequestBody JsonNode body) {
        if (!body.isObject() || (!body.has("nickname") && !body.has("representativeCharacterId")))
            throw new com.apps.mindlog.global.error.exception.ApiException(
                    com.apps.mindlog.global.error.ErrorType.VALIDATION_ERROR, "At least one profile field is required");
        if (body.has("nickname") && body.get("nickname").isNull())
            throw new com.apps.mindlog.global.error.exception.ApiException(
                    com.apps.mindlog.global.error.ErrorType.VALIDATION_ERROR, "nickname cannot be null");
        if (body.has("representativeCharacterId") && body.get("representativeCharacterId").isNull())
            throw new com.apps.mindlog.global.error.exception.ApiException(
                    com.apps.mindlog.global.error.ErrorType.VALIDATION_ERROR, "representativeCharacterId cannot be null");
        if (body.has("nickname") && !body.get("nickname").isTextual())
            throw new com.apps.mindlog.global.error.exception.ApiException(
                    com.apps.mindlog.global.error.ErrorType.VALIDATION_ERROR, "nickname must be a string");
        if (body.has("representativeCharacterId") && !body.get("representativeCharacterId").canConvertToLong())
            throw new com.apps.mindlog.global.error.exception.ApiException(
                    com.apps.mindlog.global.error.ErrorType.VALIDATION_ERROR, "representativeCharacterId must be an integer");
        UpdateProfileRequest request = objectMapper.treeToValue(body, UpdateProfileRequest.class);
        var violations = validator.validate(request);
        if (!violations.isEmpty())
            throw new com.apps.mindlog.global.error.exception.ApiException(
                    com.apps.mindlog.global.error.ErrorType.VALIDATION_ERROR, "Profile fields are invalid");
        return profiles.updateProfile(request);
    }
}
