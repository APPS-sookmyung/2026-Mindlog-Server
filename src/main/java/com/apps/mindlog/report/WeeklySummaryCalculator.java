package com.apps.mindlog.report;

import com.apps.mindlog.global.config.TimeConfig;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WeeklySummaryCalculator {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public WeeklySummaryCalculator(JdbcTemplate jdbc, Clock clock) { this.jdbc = jdbc; this.clock = clock; }

    public enum Trend { UP, DOWN, SAME, NO_PREVIOUS, NO_RECORD }
    public record DailyBar(LocalDate date, String dayOfWeek, BigDecimal averageActualScore, long recordCount) {}
    public record Summary(LocalDate periodStart, LocalDate periodEnd, String weekStartDay,
                          long beforeLogCount, long afterLogCount, BigDecimal averageExpectedScore,
                          BigDecimal averageActualScore, List<DailyBar> dailyBars,
                          BigDecimal previousWeekAverageActualScore, Integer differencePoints,
                          Trend trend, String trendMessage) {
        public Summary { dailyBars = List.copyOf(dailyBars); }
    }
    private record Total(long count, long sum) {
        BigDecimal average() { return count == 0 ? null : BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(count), 6, RoundingMode.HALF_UP); }
        int rounded() { return BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(count), 0, RoundingMode.HALF_UP).intValueExact(); }
        Total plus(Total other) { return new Total(count + other.count, sum + other.sum); }
    }
    private record DatedTotal(LocalDate date, Total total) {}

    /** Read each section from one snapshot so a concurrent final save cannot split bars and totals. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Summary calculate(long userId) {
        String weekStart = jdbc.queryForObject("SELECT weekly_report_start_day FROM users WHERE id = ?", String.class, userId);
        DayOfWeek first = switch (weekStart) {
            case "sun" -> DayOfWeek.SUNDAY;
            case "mon" -> DayOfWeek.MONDAY;
            default -> throw new IllegalStateException("Unsupported week start day");
        };
        LocalDate start = LocalDate.now(clock.withZone(TimeConfig.SEOUL)).with(TemporalAdjusters.previousOrSame(first));
        LocalDate end = start.plusWeeks(1);
        var rows = jdbc.query("""
                SELECT (a.finalized_at AT TIME ZONE 'Asia/Seoul')::date AS record_day,
                       count(*) record_count, sum(a.actual_score) score_sum
                FROM after_logs a JOIN before_logs b ON b.id = a.before_log_id
                WHERE b.user_id = ? AND a.record_status = 'FINALIZED'
                  AND a.finalized_at >= ? AND a.finalized_at < ?
                GROUP BY record_day
                """, (rs, row) -> new DatedTotal(rs.getObject("record_day", LocalDate.class),
                new Total(rs.getLong("record_count"), rs.getLong("score_sum"))),
                userId, atMidnight(start.minusWeeks(1)), atMidnight(end));
        var days = new HashMap<LocalDate, Total>();
        Total current = new Total(0, 0), previous = new Total(0, 0);
        for (var row : rows) {
            days.put(row.date(), row.total());
            if (row.date().isBefore(start)) previous = previous.plus(row.total());
            else current = current.plus(row.total());
        }
        var expected = jdbc.queryForObject("""
                SELECT count(*) record_count, COALESCE(sum(expected_score), 0) score_sum
                FROM before_logs WHERE user_id = ? AND created_at >= ? AND created_at < ?
                """, (rs, row) -> new Total(rs.getLong("record_count"), rs.getLong("score_sum")),
                userId, atMidnight(start), atMidnight(end));
        var bars = new ArrayList<DailyBar>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = start.plusDays(i);
            var total = days.getOrDefault(date, new Total(0, 0));
            bars.add(new DailyBar(date, date.getDayOfWeek().name().substring(0, 3), total.average(), total.count()));
        }
        Integer difference = current.count() == 0 || previous.count() == 0 ? null : current.rounded() - previous.rounded();
        Trend trend;
        String message;
        if (current.count() == 0) {
            trend = Trend.NO_RECORD;
            message = "불안한 상황이 없었어요";
        } else if (previous.count() == 0) {
            trend = Trend.NO_PREVIOUS;
            message = "지난 주보다 " + current.rounded() + "점 상승했어요";
        } else if (difference < 0) {
            trend = Trend.DOWN;
            message = "지난 주보다 " + Math.abs(difference) + "점 떨어졌어요";
        } else {
            trend = difference == 0 ? Trend.SAME : Trend.UP;
            message = "지난 주보다 " + difference + "점 상승했어요";
        }
        return new Summary(start, end.minusDays(1), weekStart, expected.count(), current.count(), expected.average(),
                current.average(), bars, previous.average(), difference, trend, message);
    }

    private static Timestamp atMidnight(LocalDate date) { return Timestamp.from(date.atStartOfDay(TimeConfig.SEOUL).toInstant()); }
}
