package com.apps.mindlog.reference.service;

import com.apps.mindlog.global.error.ErrorType;
import com.apps.mindlog.global.error.exception.ApiException;
import com.apps.mindlog.reference.entity.*;
import com.apps.mindlog.reference.repository.*;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReferenceValidator {
    private final EmotionCharacterRepository emotions;
    private final SituationTypeRepository situations;
    private final BodySymptomRepository symptoms;
    private final DistortionTagRepository distortions;

    public ReferenceValidator(EmotionCharacterRepository emotions, SituationTypeRepository situations,
            BodySymptomRepository symptoms, DistortionTagRepository distortions) {
        this.emotions = emotions;
        this.situations = situations;
        this.symptoms = symptoms;
        this.distortions = distortions;
    }

    public EmotionCharacter requireEmotion(Long id) {
        return require(id, emotions::findById, value -> true);
    }

    public EmotionCharacter requireEmotionCode(String code) {
        if (code == null) throw invalid();
        return emotions.findAllByOrderByDisplayOrderAscIdAsc().stream()
                .filter(value -> value.isActive() && code.equals(value.getCode())).findFirst().orElseThrow(ReferenceValidator::invalid);
    }

    public SituationType requireSituation(Long id, SituationContext context) {
        if (context == null) throw invalid();
        return require(id, situations::findById, value -> context == SituationContext.ONBOARDING
                ? value.isOnboardingSelectable() : value.isBeforeSelectable());
    }

    public List<SituationType> requireSituations(List<Long> ids, SituationContext context) {
        if (context == null) throw invalid();
        return requireList(ids, situations::findAllById, value -> context == SituationContext.ONBOARDING
                ? value.isOnboardingSelectable() : value.isBeforeSelectable());
    }

    public List<BodySymptom> requireSymptoms(List<Long> ids, SymptomContext context) {
        if (context == null) throw invalid();
        return requireList(ids, symptoms::findAllById, value -> context == SymptomContext.ONBOARDING
                ? value.isOnboardingSelectable() : value.isAfterSelectable());
    }

    public DistortionTag requireDistortionTag(Long id) {
        return require(id, distortions::findById, value -> true);
    }

    private <T extends ReferenceEntity> T require(Long id,
            Function<Long, java.util.Optional<T>> lookup, Predicate<T> allowed) {
        if (id == null || id <= 0) throw invalid();
        return lookup.apply(id).filter(value -> value.isActive() && allowed.test(value)).orElseThrow(ReferenceValidator::invalid);
    }

    private <T extends ReferenceEntity> List<T> requireList(List<Long> ids,
            Function<Iterable<Long>, List<T>> lookup, Predicate<T> allowed) {
        if (ids == null || ids.isEmpty() || ids.stream().anyMatch(id -> id == null || id <= 0)
                || new HashSet<>(ids).size() != ids.size()) throw invalid();
        List<T> values = lookup.apply(ids);
        if (values.size() != ids.size() || values.stream().anyMatch(value -> !value.isActive() || !allowed.test(value))) throw invalid();
        return values.stream().sorted(Comparator.comparingInt(ReferenceEntity::getDisplayOrder)
                .thenComparing(ReferenceEntity::getId)).toList();
    }

    private static ApiException invalid() {
        return new ApiException(ErrorType.VALIDATION_ERROR, "선택한 항목을 확인해 주세요.");
    }

    public enum SituationContext { ONBOARDING, BEFORE }
    public enum SymptomContext { ONBOARDING, AFTER }
}
