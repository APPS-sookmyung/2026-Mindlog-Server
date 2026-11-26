package com.apps.mindlog.auth.dto;

import com.apps.mindlog.global.validation.VisibleLength;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SignupRequest(
        @NotBlank String signupType,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 20) @Pattern(regexp = "(?=.*[A-Za-z])(?=.*[0-9])(?=.*[~!@#$%^&*()_?\\-])[A-Za-z0-9~!@#$%^&*()_?\\-]+") String password,
        String signupToken,
        @NotNull @Size(min = 4, max = 4) List<@NotNull @Valid Agreement> agreements,
        @NotNull @Valid Profile profile,
        String deviceToken) {

    public record Agreement(@NotBlank String agreementType, @NotBlank String agreementVersion,
            @NotNull Boolean agreed) { }

    public record Profile(@NotBlank @VisibleLength(min = 1, max = 20) String nickname,
            @NotNull @Min(1) Long representativeCharacterId,
            @NotNull @Pattern(regexp = "female|male") String gender,
            @NotNull @Min(1900) @Max(9999) Integer birthYear) { }
}
