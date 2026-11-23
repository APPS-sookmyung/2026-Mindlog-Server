package com.apps.mindlog.report;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class StreakCalculatorTest {
    private final LocalDate today = LocalDate.of(2026, 11, 23);
    @Test void countsDistinctActivityDaysEndingTodayOrYesterday() {
        var dates = List.of(today, today, today.minusDays(1), today.minusDays(2), today.plusDays(1));
        assertThat(StreakCalculator.fromDates(dates, today)).isEqualTo(new StreakCalculator.Streak(3, 3, today));
        assertThat(StreakCalculator.fromDates(List.of(today.minusDays(1), today.minusDays(2)), today))
                .isEqualTo(new StreakCalculator.Streak(2, 2, today.minusDays(1)));
    }
    @Test void aGapResetsCurrentButPreservesLongestAndLastActivity() {
        assertThat(StreakCalculator.fromDates(List.of(today.minusDays(2), today.minusDays(3), today.minusDays(4)), today))
                .isEqualTo(new StreakCalculator.Streak(0, 3, today.minusDays(2)));
        assertThat(StreakCalculator.fromDates(List.of(), today)).isEqualTo(new StreakCalculator.Streak(0, 0, null));
    }
    @Test void crossesMonthAndYearBoundaries() {
        var janFirst = LocalDate.of(2027, 1, 1);
        assertThat(StreakCalculator.fromDates(List.of(janFirst, janFirst.minusDays(1), janFirst.minusDays(2)), janFirst).current())
                .isEqualTo(3);
    }
}
