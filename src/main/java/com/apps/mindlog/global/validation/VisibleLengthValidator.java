package com.apps.mindlog.global.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.text.BreakIterator;
import java.util.Locale;

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
        // BreakIterator is mutable: create one per invocation for concurrent validation.
        BreakIterator characters = BreakIterator.getCharacterInstance(Locale.ROOT);
        characters.setText(value.strip());
        characters.first();
        int count = 0;
        while (characters.next() != BreakIterator.DONE) {
            if (++count > max) return false;
        }
        return count >= min;
    }
}
