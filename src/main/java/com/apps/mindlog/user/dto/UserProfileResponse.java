package com.apps.mindlog.user.dto;

import com.apps.mindlog.reference.entity.EmotionCharacter;
import com.apps.mindlog.user.entity.User;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public record UserProfileResponse(
        Long id,
        String email,
        String nickname,
        Long representativeCharacterId,
        CharacterSummary representativeCharacter,
        String currentEmotionCode,
        Integer baselineDiscomfortScore,
        String gender,
        Integer birthYear,
        String loginProvider,
        boolean onboardingCompleted,
        OffsetDateTime onboardingCompletedAt,
        OffsetDateTime createdAt) {

    public static UserProfileResponse from(User user) {
        EmotionCharacter character = user.getRepresentativeCharacter();
        return new UserProfileResponse(user.getId(), user.getEmail(), user.getNickname(), character.getId(),
                new CharacterSummary(character.getId(), character.getCode(), character.getName()),
                user.getCurrentEmotionCode(), user.getBaselineDiscomfortScore(), user.getGender(), user.getBirthYear(),
                user.getLoginProvider(), user.isOnboardingCompleted(), seoul(user.getOnboardingCompletedAt()), seoul(user.getCreatedAt()));
    }

    private static OffsetDateTime seoul(Instant value) {
        return value == null ? null : value.atZone(ZoneId.of("Asia/Seoul")).toOffsetDateTime();
    }

    public record CharacterSummary(Long id, String code, String name) { }
}
