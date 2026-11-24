package com.apps.mindlog.user.controller;

import com.apps.mindlog.user.dto.UpdateProfileRequest;
import com.apps.mindlog.user.dto.UserProfileResponse;
import com.apps.mindlog.user.service.UserProfileService;
import jakarta.validation.Validator;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/users/me", version = "1")
public class UserController {
    private final UserProfileService profiles;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public UserController(UserProfileService profiles, ObjectMapper objectMapper, Validator validator) {
        this.profiles = profiles;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    @GetMapping
    public UserProfileResponse me() { return profiles.me(); }

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
