package com.apps.mindlog.global.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/** Counts visible characters after stripping surrounding whitespace. Combine with @NotNull for required fields. */
@Documented
@Constraint(validatedBy = VisibleLengthValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT,
        ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface VisibleLength {
    String message() default "보이는 글자 수는 {min}자 이상 {max}자 이하여야 합니다.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    int min() default 1;
    int max() default 300;
}
