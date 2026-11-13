package com.apps.mindlog.global.validation;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

class VisibleLengthValidatorTest {
    static ValidatorFactory factory;
    static Validator validator;
    @BeforeAll static void start() { factory = Validation.buildDefaultValidatorFactory(); validator = factory.getValidator(); }
    @AfterAll static void stop() { factory.close(); }
    record One(@VisibleLength(max = 1) String text) {}
    record Before(@NotNull @VisibleLength String text) {}
    record Diary(@VisibleLength(max = 5000) String text) {}

    @ParameterizedTest
    @ValueSource(strings = {"가", "e\u0301", "\u1100\u1161", "😀", "👨‍👩‍👧‍👦", "👍🏽", "🇰🇷", "  가\n", "\u2003가\u2003"})
    void countsGraphemeClustersAsOne(String text) { assertThat(validator.validate(new One(text))).isEmpty(); }

    @ParameterizedTest @ValueSource(strings = {"", " ", "\n\t", "가나", "😀😀"})
    void rejectsOutsideOneCharacterBounds(String text) { assertThat(validator.validate(new One(text))).hasSize(1); }

    @Test void countsInternalSpacesAndLineBreaks() {
        assertThat(validator.validate(new Before("가".repeat(298) + "\n나"))).isEmpty();
        assertThat(validator.validate(new Before("가".repeat(299) + "\n나"))).hasSize(1);
        assertThat(validator.validate(new Before("가".repeat(299) + " 나"))).hasSize(1);
        assertThat(validator.validate(new Before("가".repeat(298) + "\r\n나"))).isEmpty();
    }
    @Test void testsOriginalAndDiaryBoundaries() {
        assertThat(validator.validate(new Before("😀".repeat(300)))).isEmpty();
        assertThat(validator.validate(new Before("😀".repeat(301)))).hasSize(1);
        assertThat(validator.validate(new Diary("e\u0301".repeat(5000)))).isEmpty();
        assertThat(validator.validate(new Diary("e\u0301".repeat(5001)))).hasSize(1);
    }
    @Test void nullIsHandledByRequiredConstraint() {
        assertThat(validator.validate(new One(null))).isEmpty();
        assertThat(validator.validate(new Before(null))).hasSize(1);
    }
    @Test void concurrentValidationDoesNotShareIteratorState() {
        assertThat(java.util.stream.IntStream.range(0, 100).parallel()
                .allMatch(i -> validator.validate(new One("👨‍👩‍👧‍👦")).isEmpty())).isTrue();
    }
}
