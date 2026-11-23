package com.apps.mindlog.reference;

import com.apps.mindlog.global.config.JpaConfig;
import com.apps.mindlog.global.config.TimeConfig;
import com.apps.mindlog.global.error.ErrorType;
import com.apps.mindlog.global.error.exception.ApiException;
import com.apps.mindlog.reference.service.ReferenceValidator;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.apps.mindlog.reference.service.ReferenceValidator.SituationContext.*;
import static com.apps.mindlog.reference.service.ReferenceValidator.SymptomContext.*;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaConfig.class, TimeConfig.class, ReferenceRepositoryTest.Database.class, ReferenceValidator.class})
class ReferenceValidatorTest {
    @Autowired ReferenceValidator validator;
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager entities;

    @Test void acceptsValidSelectionsAndSortsByCatalogOrder() {
        assertThat(validator.requireEmotion(2L).getCode()).isEqualTo("neutral");
        assertThat(validator.requireEmotionCode("anger").getId()).isEqualTo(1L);
        assertThat(validator.requireSituation(1L, BEFORE).getName()).isEqualTo("발표");
        assertThat(validator.requireSymptoms(List.of(9L,1L), AFTER)).extracting(value -> value.getId()).containsExactly(1L,9L);
    }

    @Test void rejectsOnboardingOnlyOptionsInRecordContexts() {
        assertThat(validator.requireSituation(2L, ReferenceValidator.SituationContext.ONBOARDING).getName()).isEqualTo("팀플");
        assertThat(validator.requireSymptoms(List.of(8L), ReferenceValidator.SymptomContext.ONBOARDING)).hasSize(1);
        invalid(() -> validator.requireSituation(2L, BEFORE));
        invalid(() -> validator.requireSituation(4L, BEFORE));
        invalid(() -> validator.requireSymptoms(List.of(8L), AFTER));
    }

    @Test void rejectsAbsentInactiveAndWrongCodesWithoutExposingIds() {
        invalid(() -> validator.requireEmotion(999L));
        invalid(() -> validator.requireEmotion(null));
        invalid(() -> validator.requireEmotionCode("ANGER"));
        invalid(() -> validator.requireEmotionCode(null));
        jdbc.update("UPDATE emotion_characters SET active=false WHERE id=1");
        entities.clear();
        invalid(() -> validator.requireEmotion(1L));
        invalid(() -> validator.requireEmotionCode("anger"));
        jdbc.update("UPDATE situation_types SET active=false WHERE id=1");
        entities.clear();
        invalid(() -> validator.requireSituation(1L, BEFORE));
    }

    @Test void rejectsInvalidOrDuplicateListsAndContexts() {
        invalid(() -> validator.requireSymptoms(null, AFTER));
        invalid(() -> validator.requireSymptoms(List.of(), AFTER));
        invalid(() -> validator.requireSymptoms(Arrays.asList(1L,null), AFTER));
        invalid(() -> validator.requireSymptoms(List.of(1L,1L), AFTER));
        invalid(() -> validator.requireSymptoms(List.of(999L), AFTER));
        invalid(() -> validator.requireSymptoms(List.of(1L), null));
        invalid(() -> validator.requireSituations(List.of(1L,1L), BEFORE));
        invalid(() -> validator.requireSituation(1L, null));
        jdbc.update("UPDATE body_symptoms SET active=false WHERE id=1");
        invalid(() -> validator.requireSymptoms(List.of(1L), AFTER));
    }

    @Test void validatesActiveDistortionReferences() {
        invalid(() -> validator.requireDistortionTag(100L));
        jdbc.update("""
                INSERT INTO distortion_tags(id,name,definition,example_thoughts,self_questions,reframes,display_order,
                    active,created_at,updated_at)
                VALUES(100,'fixture','fixture','[]','[]','[]',1,true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """);
        assertThat(validator.requireDistortionTag(100L).getId()).isEqualTo(100L);
    }

    private void invalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ApiException.class, exception -> {
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.VALIDATION_ERROR);
            assertThat(exception.getMessage()).isEqualTo("선택한 항목을 확인해 주세요.");
        });
    }
}
