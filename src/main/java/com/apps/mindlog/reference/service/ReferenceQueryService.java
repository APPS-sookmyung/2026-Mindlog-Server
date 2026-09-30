package com.apps.mindlog.reference.service;

import com.apps.mindlog.global.error.ErrorType;
import com.apps.mindlog.global.error.exception.ApiException;
import com.apps.mindlog.reference.dto.ReferenceResponses.*;
import com.apps.mindlog.reference.repository.*;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReferenceQueryService {
    private static final Map<String,String> CATEGORIES = Map.of(
            "알아차리기","awareness","근거 확인","evidence_check","다르게 생각하기","reframing",
            "몸 진정하기","body_regulation","회복력","resilience");
    private final EmotionCharacterRepository emotions;
    private final SituationTypeRepository situations;
    private final BodySymptomRepository symptoms;
    private final DistortionTagRepository distortions;
    private final PositiveSolutionRepository solutions;
    private final AnxietyPatternRepository patterns;

    public ReferenceQueryService(EmotionCharacterRepository emotions, SituationTypeRepository situations,
            BodySymptomRepository symptoms, DistortionTagRepository distortions,
            PositiveSolutionRepository solutions, AnxietyPatternRepository patterns) {
        this.emotions=emotions; this.situations=situations; this.symptoms=symptoms;
        this.distortions=distortions; this.solutions=solutions; this.patterns=patterns;
    }

    public Catalog<Choice> situations(String context, boolean includeInactive) {
        if (!"onboarding".equals(context) && !"before".equals(context)) throw invalid();
        return new Catalog<>(situations.findAllByOrderByDisplayOrderAscIdAsc().stream()
                .filter(value -> includeInactive || value.isActive())
                .filter(value -> "onboarding".equals(context) ? value.isOnboardingSelectable() : value.isBeforeSelectable())
                .map(value -> new Choice(value.getId(),value.getName())).toList());
    }

    public Catalog<DescribedChoice> symptoms(String context, boolean includeInactive) {
        if (!"onboarding".equals(context) && !"after".equals(context)) throw invalid();
        return new Catalog<>(symptoms.findAllByOrderByDisplayOrderAscIdAsc().stream()
                .filter(value -> includeInactive || value.isActive())
                .filter(value -> "onboarding".equals(context) ? value.isOnboardingSelectable() : value.isAfterSelectable())
                .map(value -> new DescribedChoice(value.getId(),value.getName(),value.getDescription())).toList());
    }

    public Catalog<Emotion> emotions(boolean includeInactive) {
        return new Catalog<>(emotions.findAllByOrderByDisplayOrderAscIdAsc().stream()
                .filter(value -> includeInactive || value.isActive())
                .map(value -> new Emotion(value.getId(),value.getCode(),value.getName())).toList());
    }

    public Catalog<DescribedChoice> patterns(boolean includeInactive) {
        return new Catalog<>(patterns.findAllByOrderByDisplayOrderAscIdAsc().stream()
                .filter(value -> includeInactive || value.isActive())
                .map(value -> new DescribedChoice(value.getId(),value.getName(),value.getDescription())).toList());
    }

    public Catalog<DescribedChoice> distortions(boolean includeInactive) {
        return new Catalog<>(distortions.findAllByOrderByDisplayOrderAscIdAsc().stream()
                .filter(value -> includeInactive || value.isActive())
                .map(value -> new DescribedChoice(value.getId(),value.getName(),value.getDefinition())).toList());
    }

    public Guide guide(long id) {
        var value = distortions.findById(id).orElseThrow(() ->
                new ApiException(ErrorType.RESOURCE_NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."));
        return new Guide(value.getId(),value.getName(),value.getIllustrationKey(),value.getDefinition(),
                value.getExampleThoughts(),value.getSelfQuestions(),value.getReframes(),value.isActive());
    }

    public Catalog<Solution> solutions(String category, boolean includeInactive) {
        String code = category == null ? null : CATEGORIES.get(category);
        if (category != null && code == null) throw invalid();
        return new Catalog<>(solutions.findAllByOrderByDisplayOrderAscIdAsc().stream()
                .filter(value -> includeInactive || value.isActive())
                .filter(value -> code == null || code.equals(value.getCategoryCode()))
                .map(value -> new Solution(value.getId(),value.getCategoryName(),value.getTitle(),
                        value.getSolutionContent(),value.getDisplayOrder())).toList());
    }

    public java.util.Map<Long,String> distortionNames(java.util.List<Long> ids) {
        return distortions.findAllById(ids).stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                value -> value.getId(),value -> value.getName()));
    }

    public void requireExistingSituation(long id) {
        if (id <= 0 || !situations.existsById(id)) throw invalid();
    }

    /** Historical record labels remain visible even after a choice becomes inactive. */
    public java.util.List<Choice> symptomsByIds(java.util.List<Long> ids) {
        return symptoms.findAllById(ids).stream()
                .sorted(java.util.Comparator.comparingInt(com.apps.mindlog.reference.entity.BodySymptom::getDisplayOrder)
                        .thenComparing(com.apps.mindlog.reference.entity.BodySymptom::getId))
                .map(value -> new Choice(value.getId(),value.getName())).toList();
    }

    private static ApiException invalid() {
        return new ApiException(ErrorType.VALIDATION_ERROR, "조회 조건을 확인해 주세요.");
    }
}
