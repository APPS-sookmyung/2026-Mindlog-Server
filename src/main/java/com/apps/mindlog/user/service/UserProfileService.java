package com.apps.mindlog.user.service;

import com.apps.mindlog.global.config.TimeConfig;
import com.apps.mindlog.global.error.ErrorType;
import com.apps.mindlog.global.error.exception.ApiException;
import com.apps.mindlog.global.security.AccountAccess;
import com.apps.mindlog.reference.entity.EmotionCharacter;
import com.apps.mindlog.reference.repository.EmotionCharacterRepository;
import com.apps.mindlog.user.dto.UpdateProfileRequest;
import com.apps.mindlog.user.dto.UserProfileResponse;
import com.apps.mindlog.user.entity.User;
import com.apps.mindlog.user.repository.UserRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserProfileService {
    private final AccountAccess accounts;
    private final UserRepository users;
    private final EmotionCharacterRepository characters;
    private final Clock clock;

    public UserProfileService(AccountAccess accounts, UserRepository users, EmotionCharacterRepository characters, Clock clock) {
        this.accounts = accounts;
        this.users = users;
        this.characters = characters;
        this.clock = clock;
    }

    public UserProfileResponse me() {
        long id = accounts.current().id();
        User user = users.findProfileById(id).orElseThrow(() -> new ApiException(ErrorType.RESOURCE_NOT_FOUND));
        user.setLoginProvider(users.existsGoogleAccount(id) ? "google" : "email");
        return UserProfileResponse.from(user);
    }

    @Transactional
    public ProfileUpdateResponse updateProfile(UpdateProfileRequest request) {
        Long id = accounts.current().id();
        User user = users.findByIdAndAccountStatusNot(id, "deleting")
                .orElseThrow(() -> new ApiException(ErrorType.INVALID_TOKEN));
        String nickname = request.nickname() == null ? user.getNickname() : request.nickname().strip();
        EmotionCharacter character = request.representativeCharacterId() == null
                ? user.getRepresentativeCharacter()
                : characters.findById(request.representativeCharacterId())
                        .filter(EmotionCharacter::isActive)
                        .orElseThrow(() -> new ApiException(ErrorType.VALIDATION_ERROR, "representativeCharacterId must be active"));
        if (nickname.isEmpty() || nickname.length() > 20 || nickname.indexOf('\n') >= 0 || nickname.indexOf('\r') >= 0)
            throw new ApiException(ErrorType.VALIDATION_ERROR, "nickname must contain 1 to 20 characters without line breaks");

        boolean changed = !Objects.equals(nickname, user.getNickname())
                || !Objects.equals(character.getId(), user.getRepresentativeCharacter().getId());
        if (changed) {
            user.updateProfile(nickname, character);
            user.touch(OffsetDateTime.now(clock).toInstant());
        }
        return new ProfileUpdateResponse(user.getNickname(),
                new UserProfileResponse.CharacterSummary(character.getId(), character.getCode(), character.getName()),
                user.getUpdatedAt().atZone(TimeConfig.SEOUL).toOffsetDateTime());
    }

    public record ProfileUpdateResponse(String nickname, UserProfileResponse.CharacterSummary representativeCharacter,
            OffsetDateTime updatedAt) { }
}
