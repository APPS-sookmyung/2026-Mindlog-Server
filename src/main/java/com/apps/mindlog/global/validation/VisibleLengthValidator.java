package com.apps.mindlog.global.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class VisibleLengthValidator implements ConstraintValidator<VisibleLength, String> {
    private int min;
    private int max;

    @Override
    public void initialize(VisibleLength constraint) {
        min = constraint.min();
        max = constraint.max();
        if (min < 0 || max < min) throw new IllegalArgumentException("Invalid visible length bounds");
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return true;
        int count = VisibleText.length(value);
        return count >= min && count <= max;
    }
}
