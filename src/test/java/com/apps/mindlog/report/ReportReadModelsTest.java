package com.apps.mindlog.report;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class ReportReadModelsTest {
    @Container static final PostgreSQLContainer DB = new PostgreSQLContainer("postgres:16-alpine");
    private JdbcTemplate jdbc;
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-23T00:00:00Z"), ZoneOffset.UTC);
    private long id;

    @BeforeEach void setup() {
        var source = new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
        jdbc = new JdbcTemplate(source);
        jdbc.execute("DROP SCHEMA public CASCADE"); jdbc.execute("CREATE SCHEMA public");
        Flyway.configure().dataSource(source).load().migrate();
        for (int user = 900; user <= 901; user++) jdbc.update("""
                INSERT INTO users(id,email,nickname,representative_character_id,gender,birth_year,account_status,
                  onboarding_completed,after_reminder_enabled,weekly_report_start_day,created_at,updated_at)
                VALUES(?,?,'예선',1,'female',2000,'active',true,true,'mon','2026-11-01T00:00:00Z','2026-11-01T00:00:00Z')
                """, user, user + "@report.test");
        id = 1000;
    }
    private long before(long user, String createdAt, int expected) {
        long next = ++id;
        jdbc.update("""
                INSERT INTO before_logs(id,user_id,situation_type_id,title,scheduled_at,expected_score,worst_scenario,created_at,updated_at)
                VALUES(?,?,1,?,'2026-12-01',?,'걱정',?,?)
                """, next, user, "기록 " + next, expected, stamp(createdAt), stamp(createdAt));
        return next;
    }
    private void after(long before, String created, String finalized, Integer score) {
        jdbc.update("""
                INSERT INTO after_logs(id,before_log_id,free_writing,record_status,actual_score,final_diary,finalized_at,created_at,updated_at)
                VALUES(?,?,'원문',?,?,?,?,?,?)
                """, before, before, finalized == null ? "DRAFT" : "FINALIZED", score,
                finalized == null ? null : "일기", finalized == null ? null : stamp(finalized), stamp(created),
                stamp(finalized == null ? created : finalized));
    }
    private static Timestamp stamp(String value) { return Timestamp.from(Instant.parse(value)); }
    private WeeklySummaryCalculator.Summary weekly() { return new WeeklySummaryCalculator(jdbc, clock).calculate(900); }

    @Test void weeklyCountsOnlyFinalizedRecordsAndWeightsRecordsRatherThanDailyMeans() {
        long older = before(900, "2026-11-10T00:00:00Z", 80);
        after(older, "2026-11-10T00:00:00Z", "2026-11-22T15:00:00Z", 0);
        long second = before(900, "2026-11-23T00:00:00Z", 60);
        after(second, "2026-11-23T00:00:00Z", "2026-11-23T01:00:00Z", 100);
        long third = before(900, "2026-11-24T00:00:00Z", 70);
        after(third, "2026-11-24T00:00:00Z", "2026-11-24T00:00:00Z", 20);
        long draft = before(900, "2026-11-23T01:00:00Z", 100);
        after(draft, "2026-11-23T01:00:00Z", null, null);
        long foreign = before(901, "2026-11-23T00:00:00Z", 100);
        after(foreign, "2026-11-23T00:00:00Z", "2026-11-23T00:00:00Z", 100);

        var summary = weekly();
        assertThat(summary.periodStart()).hasToString("2026-11-23");
        assertThat(summary.periodEnd()).hasToString("2026-11-29");
        assertThat(summary.afterLogCount()).isEqualTo(3);
        assertThat(summary.beforeLogCount()).isEqualTo(3);
        assertThat(summary.averageActualScore()).isEqualByComparingTo("40");
        assertThat(summary.dailyBars()).hasSize(7);
        assertThat(summary.dailyBars().getFirst().recordCount()).isEqualTo(2);
        assertThat(summary.dailyBars().getFirst().averageActualScore()).isEqualByComparingTo("50");
        assertThat(summary.dailyBars().get(2).averageActualScore()).isNull();
        assertThat(summary.trend()).isEqualTo(WeeklySummaryCalculator.Trend.NO_PREVIOUS);
        assertThat(summary.differencePoints()).isNull();
        assertThat(summary.trendMessage()).isEqualTo("지난 주보다 40점 상승했어요");
    }

    @Test void zeroAndEmptyWeeksRemainDistinctAndKoreanMidnightIsHalfOpen() {
        long prior = before(900, "2026-11-22T14:59:59Z", 80);
        after(prior, "2026-11-22T14:59:59Z", "2026-11-22T14:59:59Z", 80);
        assertThat(weekly().averageActualScore()).isNull();
        assertThat(weekly().trend()).isEqualTo(WeeklySummaryCalculator.Trend.NO_RECORD);
        long current = before(900, "2026-11-22T15:00:00Z", 50);
        after(current, "2026-11-22T15:00:00Z", "2026-11-22T15:00:00Z", 0);
        long nextWeek = before(900, "2026-11-29T15:00:00Z", 100);
        after(nextWeek, "2026-11-29T15:00:00Z", "2026-11-29T15:00:00Z", 100);
        var summary = weekly();
        assertThat(summary.averageActualScore()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.dailyBars().getFirst().averageActualScore()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.previousWeekAverageActualScore()).isEqualByComparingTo("80");
        assertThat(summary.differencePoints()).isEqualTo(-80);
        assertThat(summary.trend()).isEqualTo(WeeklySummaryCalculator.Trend.DOWN);
    }

    @Test void respectsSundaySettingsAndRoundsBothAveragesBeforeSubtracting() {
        jdbc.update("UPDATE users SET weekly_report_start_day='sun' WHERE id=900");
        for (int score : new int[]{0, 1}) {
            long record = before(900, "2026-11-21T15:00:00Z", 80);
            after(record, "2026-11-21T15:00:00Z", "2026-11-21T15:00:00Z", score);
        }
        for (int score : new int[]{0, 1, 0}) {
            long record = before(900, "2026-11-14T15:00:00Z", 70);
            after(record, "2026-11-14T15:00:00Z", "2026-11-14T15:00:00Z", score);
        }
        var summary = weekly();
        assertThat(summary.periodStart()).hasToString("2026-11-22");
        assertThat(summary.dailyBars().getFirst().dayOfWeek()).isEqualTo("SUN");
        assertThat(summary.differencePoints()).isEqualTo(1);
        assertThat(summary.trend()).isEqualTo(WeeklySummaryCalculator.Trend.UP);
    }

    @Test void recentThreeAreOrderedByBeforeCreationIncludingDraftsAndMissingAfters() {
        assertThat(new RecordListQuery(jdbc).recent(900)).isEmpty();
        long first = before(900, "2026-11-20T15:00:00Z", 80);
        long second = before(900, "2026-11-21T15:00:00Z", 70);
        after(second, "2026-11-21T15:00:00Z", null, null);
        long third = before(900, "2026-11-22T15:00:00Z", 60);
        after(third, "2026-11-22T15:00:00Z", "2026-11-22T15:00:00Z", 0);
        long fourth = before(900, "2026-11-22T15:00:00Z", 50);
        before(901, "2026-11-23T00:00:00Z", 100);
        var rows = new RecordListQuery(jdbc).recent(900);
        assertThat(rows).extracting(RecordListQuery.RecordSummary::beforeLogId).containsExactly(fourth, third, second);
        assertThat(rows.getFirst().afterLogId()).isNull();
        assertThat(rows.getFirst().recordStatus()).isEqualTo("IN_PROGRESS");
        assertThat(rows.getFirst().date()).hasToString("2026-11-23");
        assertThat(rows.get(1).actualScore()).isZero();
        assertThat(rows.get(2).actualScore()).isNull();
        assertThat(rows).extracting(RecordListQuery.RecordSummary::beforeLogId).doesNotContain(first);
    }

    @Test void draftEditsPreserveEveryActivityDayAndDeletionCascades() {
        long record = before(900, "2026-11-20T15:00:00Z", 80);
        after(record, "2026-11-20T15:00:00Z", null, null);
        jdbc.update("UPDATE after_logs SET free_writing='첫 수정',input_version=2,updated_at=? WHERE id=?", stamp("2026-11-21T15:00:00Z"), record);
        jdbc.update("UPDATE after_logs SET free_writing='둘째 수정',input_version=3,updated_at=? WHERE id=?", stamp("2026-11-22T15:00:00Z"), record);
        // Metadata-only writes must not create user activity.
        jdbc.update("UPDATE before_logs SET updated_at=? WHERE id=?", stamp("2026-11-23T15:00:00Z"), record);
        var streak = new StreakCalculator(jdbc, clock).calculate(900);
        assertThat(streak).isEqualTo(new StreakCalculator.Streak(3, 3, java.time.LocalDate.of(2026, 11, 23)));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM record_activity_days WHERE before_log_id=?", Integer.class, record)).isEqualTo(3);
        jdbc.update("DELETE FROM before_logs WHERE id=?", record);
        assertThat(new StreakCalculator(jdbc, clock).calculate(900).current()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM record_activity_days", Integer.class)).isZero();
    }

    @Test void versionNineUpgradeBackfillsKnownDatesAndNewSavesRemainTransactional() {
        var source = new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
        jdbc = new JdbcTemplate(source);
        jdbc.execute("DROP SCHEMA public CASCADE"); jdbc.execute("CREATE SCHEMA public");
        Flyway.configure().dataSource(source).target("9").load().migrate();
        jdbc.update("""
                INSERT INTO users(id,email,nickname,representative_character_id,gender,birth_year,account_status,
                  onboarding_completed,after_reminder_enabled,weekly_report_start_day,created_at,updated_at)
                VALUES(900,'upgrade@report.test','예선',1,'female',2000,'active',true,true,'mon',now(),now())
                """);
        long record = before(900, "2026-11-19T15:00:00Z", 80);
        jdbc.update("UPDATE before_logs SET updated_at=? WHERE id=?", stamp("2026-11-20T15:00:00Z"), record);
        after(record, "2026-11-21T15:00:00Z", "2026-11-22T15:00:00Z", 0);

        var migration = Flyway.configure().dataSource(source).load();
        assertThat(migration.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(migration.migrate().migrationsExecuted).isZero();
        assertThat(new StreakCalculator(jdbc, clock).calculate(900))
                .isEqualTo(new StreakCalculator.Streak(4, 4, java.time.LocalDate.of(2026, 11, 23)));

        var transaction = new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(source));
        transaction.executeWithoutResult(status -> {
            jdbc.update("UPDATE before_logs SET title='롤백할 수정',updated_at=? WHERE id=?", stamp("2026-11-23T15:00:00Z"), record);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM record_activity_days", Integer.class)).isEqualTo(5);
            status.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM record_activity_days", Integer.class)).isEqualTo(4);
    }
}
