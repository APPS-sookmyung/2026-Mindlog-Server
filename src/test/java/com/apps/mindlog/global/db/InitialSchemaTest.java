package com.apps.mindlog.global.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class InitialSchemaTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    static Flyway flyway;

    @BeforeAll
    static void migrate() {
        flyway = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword()).cleanDisabled(true).baselineOnMigrate(false).target("1").load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
    }

    @BeforeEach
    void resetFixtures() throws SQLException {
        sql("TRUNCATE users, emotion_characters, situation_types, body_symptoms, distortion_tags, anxiety_patterns CASCADE");
        insert("emotion_characters", "id,code,name,display_order,active", "1,'neutral','보통',1,true");
        insert("situation_types", "id,name,onboarding_selectable,before_selectable,display_order,active",
                "1,'발표',true,true,1,true");
        insert("users", "id,email,nickname,representative_character_id,account_status,after_reminder_enabled,weekly_report_start_day",
                "1,'user@example.com','테스트',1,'active',true,'mon'");
        before(1);
        after(1, 1);
    }

    @Test
    void migrationIsRepeatableWithoutDroppingData() throws SQLException {
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        flyway.validate();
        assertThat(count("users")).isEqualTo(1);
        assertThat(number("SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE'"))
                .isEqualTo(29); // 28 domain tables plus Flyway history.
        assertThatThrownBy(() -> flyway.clean()).hasMessageContaining("cleanDisabled");
    }

    @Test
    void existingUnmanagedSchemaIsNotAutomaticallyBaselined() throws SQLException {
        sql("CREATE SCHEMA unmanaged");
        sql("CREATE TABLE unmanaged.existing_data (id bigint)");
        sql("INSERT INTO unmanaged.existing_data VALUES (1)");
        Flyway unmanaged = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword()).defaultSchema("unmanaged").baselineOnMigrate(false).load();
        assertThatThrownBy(() -> unmanaged.migrate()).hasMessageContaining("non-empty schema");
        assertThat(number("SELECT count(*) FROM unmanaged.existing_data")).isEqualTo(1);
    }

    @Test
    void emailIsCaseInsensitiveAndTrimmed() {
        violation("UPDATE users SET email=' user@example.com'", "23514", "ck_users_email_trimmed");
        violation("""
                INSERT INTO users (id,email,nickname,representative_character_id,account_status,
                    after_reminder_enabled,weekly_report_start_day,created_at,updated_at)
                VALUES (99,'USER@example.com','other',1,'active',true,'mon',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, "23505", "ux_users_email_lower");
    }

    @Test
    void afterIsUniquePerBeforeAndFinalizationIsAtomic() throws SQLException {
        violation("""
                INSERT INTO after_logs (id,before_log_id,free_writing,record_status,created_at,updated_at)
                VALUES (99,1,'중복','DRAFT',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, "23505", "ux_after_logs_before_log_id");
        violation("UPDATE after_logs SET actual_score=20 WHERE id=1", "23514", "ck_after_logs_finalization");
        violation("UPDATE after_logs SET record_status='FINALIZED' WHERE id=1", "23514", "ck_after_logs_finalization");
        sql("UPDATE after_logs SET record_status='FINALIZED',actual_score=20,final_diary='일기',finalized_at=CURRENT_TIMESTAMP WHERE id=1");
        violation("UPDATE after_logs SET actual_score=101 WHERE id=1", "23514", "ck_after_logs_actual_score");
        violation("UPDATE after_logs SET input_version=0 WHERE id=1", "23514", "ck_after_logs_input_version");
    }

    @Test
    void graphemeTextIsNotTruncatedToSqlCodePointLimits() throws SQLException {
        String value = "👨‍👩‍👧‍👦".repeat(300);
        try (Connection connection = connection();
             var statement = connection.prepareStatement("UPDATE after_logs SET free_writing=? WHERE id=1")) {
            statement.setString(1, value);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
        assertThat(number("SELECT char_length(free_writing) FROM after_logs WHERE id=1")).isGreaterThan(300);
    }

    @Test
    void activeAiWorkIsUniqueAndTerminalWorkAllowsNextRequest() throws SQLException {
        job("PENDING", UUID.randomUUID(), 1);
        violation(jobSql("PROCESSING", UUID.randomUUID(), 1), "23505", "ux_ai_jobs_active_after_kind");
        sql("UPDATE ai_jobs SET status='COMPLETED'");
        UUID key = UUID.randomUUID();
        job("PENDING", key, 1);
        violation(jobSql("FAILED", key, 1), "23505", "ux_ai_jobs_request");
        before(2);
        after(2, 2);
        job("PENDING", UUID.randomUUID(), 2);
        assertThat(count("ai_jobs")).isEqualTo(3);
    }

    @Test
    void nullableReportScopeStillPreventsDuplicateReports() throws SQLException {
        insert("user_insights", "user_id,insight_type,content,model_version,computed_at",
                "1,'growth','{}','test',CURRENT_TIMESTAMP");
        violation("""
                INSERT INTO user_insights (user_id,insight_type,content,model_version,computed_at,created_at,updated_at)
                VALUES (1,'growth','{}','test',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, "23505", "ux_user_insights_scope");
    }

    @Test
    void factorAnswerCannotReferenceAnotherAfter() throws SQLException {
        before(2);
        after(2, 2);
        insert("ai_insights", "id,after_log_id,insight_type,display_order,insight_content",
                "1,1,'external_factor',1,'외부 요인'");
        violation("""
                INSERT INTO after_factor_judgements (after_log_id,ai_insight_id,answer,created_at,updated_at)
                VALUES (2,1,true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, "23503", "fk_after_factor_judgements_insight_after");
        insert("after_factor_judgements", "after_log_id,ai_insight_id,answer", "1,1,true");
        sql("DELETE FROM ai_insights WHERE id=1");
        assertThat(count("after_factor_judgements")).isZero();
    }

    @Test
    void repeatedDistortionEvidenceIsAllowedButSymptomSelectionIsUnique() throws SQLException {
        insert("distortion_tags", "id,name,definition,example_thoughts,self_questions,reframes,display_order,active",
                "1,'개인화','정의','[]','[]','[]',1,true");
        insert("after_distortions", "after_log_id,distortion_tag_id,evidence,explanation", "1,1,'인용 1','설명'");
        insert("after_distortions", "after_log_id,distortion_tag_id,evidence,explanation", "1,1,'인용 2','설명'");
        insert("body_symptoms", "id,name,symptom_type,onboarding_selectable,after_selectable,display_order,active",
                "1,'두근거림','physical',true,true,1,true");
        insert("after_symptoms", "after_log_id,body_symptom_id", "1,1");
        violation("INSERT INTO after_symptoms(after_log_id,body_symptom_id,created_at) VALUES(1,1,CURRENT_TIMESTAMP)",
                "23505", "ux_after_symptoms_pair");
        assertThat(count("after_distortions")).isEqualTo(2);
    }

    @Test
    void notificationDedupIncludesDismissedAndCancelledRows() throws SQLException {
        notification();
        sql("UPDATE notifications SET status='dismissed'");
        violation("""
                INSERT INTO notifications(id,user_id,type,priority,before_log_id,scheduled_at,dedup_key,status,created_at,updated_at)
                VALUES(99,1,'after_induce',1,1,CURRENT_TIMESTAMP,'after:1','pending',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, "23505", "ux_notifications_user_dedup");
        sql("UPDATE notifications SET status='cancelled'");
        assertThat(count("notifications")).isEqualTo(1);
    }

    @Test
    void tokenDeletionPreservesSessionAndDeliveryHistory() throws SQLException {
        notification();
        insert("device_tokens", "id,user_id,token,platform", "1,1,'test-token','android'");
        insert("refresh_sessions", "user_id,device_token_id,refresh_token_hash,expires_at",
                "1,1,'hashed-token',CURRENT_TIMESTAMP");
        insert("notification_deliveries", "notification_id,device_token_id,channel,status", "1,1,'push','sent'");
        sql("DELETE FROM device_tokens WHERE id=1");
        assertThat(number("SELECT count(*) FROM refresh_sessions WHERE device_token_id IS NULL")).isEqualTo(1);
        assertThat(number("SELECT count(*) FROM notification_deliveries WHERE device_token_id IS NULL")).isEqualTo(1);
    }

    @Test
    void userDeletionCascadesThroughRecordsJobsAndNotificationsButKeepsMasters() throws SQLException {
        notification();
        job("PENDING", UUID.randomUUID(), 1);
        insert("notification_deliveries", "notification_id,channel,status", "1,'push','pending'");
        insert("ai_insights", "id,after_log_id,insight_type,display_order,insight_content",
                "1,1,'external_factor',1,'요인'");
        insert("after_factor_judgements", "after_log_id,ai_insight_id,answer", "1,1,true");
        sql("DELETE FROM users WHERE id=1");
        for (String table : new String[]{"before_logs","after_logs","ai_jobs","notifications",
                "notification_deliveries","ai_insights","after_factor_judgements"}) {
            assertThat(count(table)).as(table).isZero();
        }
        assertThat(count("situation_types")).isEqualTo(1);
        assertThat(count("emotion_characters")).isEqualTo(1);
    }

    @Test
    void referencedMastersCannotBeDeleted() {
        violation("DELETE FROM emotion_characters WHERE id=1", "23503", "fk_users_representative_character_id");
        violation("DELETE FROM situation_types WHERE id=1", "23503", "fk_before_logs_situation_type_id");
    }

    @Test
    void instantColumnsUseTimezoneAndBusinessDatesStayDates() throws SQLException {
        assertThat(number("""
                SELECT count(*) FROM information_schema.columns WHERE table_schema='public'
                AND table_name <> 'flyway_schema_history' AND data_type='timestamp without time zone'
                """)).isZero();
        assertThat(number("""
                SELECT count(*) FROM information_schema.columns WHERE table_schema='public'
                AND table_name='before_logs' AND column_name='scheduled_at' AND data_type='date'
                """)).isEqualTo(1);
    }

    private void before(long id) throws SQLException {
        insert("before_logs", "id,user_id,situation_type_id,scheduled_at,expected_score,worst_scenario",
                id + ",1,1,'2026-09-29',50,'걱정'");
    }

    private void after(long id, long beforeId) throws SQLException {
        insert("after_logs", "id,before_log_id,free_writing,record_status", id + "," + beforeId + ",'원문','DRAFT'");
    }

    private void notification() throws SQLException {
        insert("notifications", "id,user_id,type,priority,before_log_id,scheduled_at,dedup_key,status",
                "1,1,'after_induce',1,1,CURRENT_TIMESTAMP,'after:1','pending'");
    }

    private void job(String status, UUID key, long afterId) throws SQLException {
        sql(jobSql(status, key, afterId));
    }

    private String jobSql(String status, UUID key, long afterId) {
        return """
                INSERT INTO ai_jobs(id,user_id,after_log_id,kind,status,request_key,input_version,data_generation,
                    input_snapshot,retryable,created_at,updated_at)
                VALUES('%s',1,%d,'after_analysis','%s','%s',1,0,'{}',false,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """.formatted(UUID.randomUUID(), afterId, status, key);
    }

    private void insert(String table, String columns, String values) throws SQLException {
        boolean updatedAt = !java.util.Set.of("after_distortions", "after_symptoms", "refresh_sessions").contains(table);
        sql("INSERT INTO " + table + "(" + columns + ",created_at" + (updatedAt ? ",updated_at" : "")
                + ") VALUES(" + values + ",CURRENT_TIMESTAMP" + (updatedAt ? ",CURRENT_TIMESTAMP" : "") + ")");
    }

    private void violation(String sql, String state, String constraint) {
        assertThatThrownBy(() -> sql(sql)).isInstanceOfSatisfying(SQLException.class, exception -> {
            assertThat(exception.getSQLState()).isEqualTo(state);
            assertThat(exception.getMessage()).contains(constraint);
        });
    }

    private long count(String table) throws SQLException {
        return number("SELECT count(*) FROM " + table);
    }

    private long number(String sql) throws SQLException {
        try (Connection connection = connection(); var statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }

    private void sql(String sql) throws SQLException {
        try (Connection connection = connection(); var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
