package com.apps.mindlog.user.service;

import com.apps.mindlog.global.config.TimeConfig;
import com.apps.mindlog.global.error.ErrorType;
import com.apps.mindlog.global.error.exception.ApiException;
import com.apps.mindlog.global.security.AccountAccess;
import com.apps.mindlog.reference.entity.BodySymptom;
import com.apps.mindlog.reference.entity.EmotionCharacter;
import com.apps.mindlog.reference.entity.SituationType;
import com.apps.mindlog.reference.service.ReferenceValidator;
import com.apps.mindlog.user.dto.OnboardingRequest;
import com.apps.mindlog.user.dto.OnboardingResponse;
import com.apps.mindlog.user.entity.User;
import com.apps.mindlog.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OnboardingService {
    private final AccountAccess accounts;
    private final UserRepository users;
    private final ReferenceValidator references;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public OnboardingService(AccountAccess accounts, UserRepository users, ReferenceValidator references,
            JdbcTemplate jdbc, Clock clock) {
        this.accounts = accounts;
        this.users = users;
        this.references = references;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public OnboardingResponse complete(OnboardingRequest request) {
        long userId = accounts.current().id();
        User user = users.findForOnboardingUpdate(userId)
                .orElseThrow(() -> new ApiException(ErrorType.INVALID_TOKEN));
        if (!user.isOnboardingCompleted()) {
            validateShape(request);
            EmotionCharacter emotion = references.requireEmotionCode(request.currentEmotionCode());
            List<SituationType> situations = references.requireSituations(request.situationTypeIds(),
                    ReferenceValidator.SituationContext.ONBOARDING);
            List<BodySymptom> symptoms = references.requireSymptoms(request.bodySymptomIds(),
                    ReferenceValidator.SymptomContext.ONBOARDING);
            Instant now = Instant.now(clock);
            for (SituationType situation : situations)
                jdbc.update("insert into user_onboarding_situations(user_id,situation_type_id,created_at) values (?,?,?)",
                        userId, situation.getId(), java.sql.Timestamp.from(now));
            for (BodySymptom symptom : symptoms)
                jdbc.update("insert into user_onboarding_symptoms(user_id,body_symptom_id,created_at) values (?,?,?)",
                        userId, symptom.getId(), java.sql.Timestamp.from(now));
            user.completeOnboarding(emotion.getCode(), request.baselineDiscomfortScore(), now);
        }
        return response(user);
    }

    private OnboardingResponse response(User user) {
        List<OnboardingResponse.Selection> situations = jdbc.query(
                "select s.id,s.name from user_onboarding_situations u join situation_types s on s.id=u.situation_type_id where u.user_id=? order by s.display_order,s.id",
                (rs, row) -> new OnboardingResponse.Selection(rs.getLong("id"), rs.getString("name")), user.getId());
        List<OnboardingResponse.Selection> symptoms = jdbc.query(
                "select s.id,s.name from user_onboarding_symptoms u join body_symptoms s on s.id=u.body_symptom_id where u.user_id=? order by s.display_order,s.id",
                (rs, row) -> new OnboardingResponse.Selection(rs.getLong("id"), rs.getString("name")), user.getId());
        OffsetDateTime completedAt = user.getOnboardingCompletedAt() == null ? null
                : user.getOnboardingCompletedAt().atZone(TimeConfig.SEOUL).toOffsetDateTime();
        return new OnboardingResponse(user.getCurrentEmotionCode(), user.getBaselineDiscomfortScore(),
                situations, symptoms, user.isOnboardingCompleted(), completedAt);
    }

    private static void validateShape(OnboardingRequest request) {
        if (request == null || request.currentEmotionCode() == null || request.baselineDiscomfortScore() == null
                || request.baselineDiscomfortScore() < 0 || request.baselineDiscomfortScore() > 100
                || request.situationTypeIds() == null || request.situationTypeIds().isEmpty()
                || request.bodySymptomIds() == null || request.bodySymptomIds().isEmpty())
            throw new ApiException(ErrorType.VALIDATION_ERROR, "Invalid onboarding fields");
    }
}
