package com.apps.mindlog.report;

import com.apps.mindlog.global.config.TimeConfig;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.TreeSet;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class StreakCalculator {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public StreakCalculator(JdbcTemplate jdbc, Clock clock) { this.jdbc = jdbc; this.clock = clock; }
    public record Streak(int current, int longest, LocalDate lastActiveDate) {}

    public Streak calculate(long userId) {
        LocalDate today = LocalDate.now(clock.withZone(TimeConfig.SEOUL));
        var dates = jdbc.queryForList("""
                SELECT DISTINCT d.activity_date FROM record_activity_days d
                JOIN before_logs b ON b.id = d.before_log_id
                WHERE b.user_id = ? AND d.activity_date <= ? ORDER BY d.activity_date
                """, LocalDate.class, userId, today);
        return fromDates(dates, today);
    }

    static Streak fromDates(Collection<LocalDate> dates, LocalDate today) {
        var days = new TreeSet<LocalDate>();
        dates.stream().filter(day -> !day.isAfter(today)).forEach(days::add);
        int run = 0, longest = 0;
        LocalDate previous = null;
        for (LocalDate day : days) {
            run = previous != null && previous.plusDays(1).equals(day) ? run + 1 : 1;
            longest = Math.max(longest, run);
            previous = day;
        }
        LocalDate latest = days.isEmpty() ? null : days.last();
        int current = latest != null && !latest.isBefore(today.minusDays(1)) ? run : 0;
        return new Streak(current, longest, latest);
    }
}
