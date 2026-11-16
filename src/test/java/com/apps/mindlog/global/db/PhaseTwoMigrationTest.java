package com.apps.mindlog.global.db;

import java.util.Properties;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.*;

@Testcontainers
class PhaseTwoMigrationTest {
    @Container static final PostgreSQLContainer DB = new PostgreSQLContainer("postgres:16-alpine");
    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;
    private String schema;

    @BeforeEach void createSchema() {
        schema = "test_" + UUID.randomUUID().toString().replace("-", "");
        dataSource = new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE SCHEMA " + schema);
        var properties = new Properties();
        properties.setProperty("currentSchema", schema);
        dataSource.setConnectionProperties(properties);
    }

    private Flyway flyway(String target) {
        return Flyway.configure().dataSource(dataSource).defaultSchema(schema).target(target).load();
    }

    private void user(Long id, String gender, Integer year) {
        jdbc.update("""
                INSERT INTO users(id,email,nickname,representative_character_id,gender,birth_year,account_status,
                    after_reminder_enabled,weekly_report_start_day,created_at,updated_at)
                VALUES(?,?,'기존 계정',1,?,?,'active',true,'sun','2025-12-31T15:01:00Z',CURRENT_TIMESTAMP)
                """, id, id + "@test.example", gender, year);
    }

    @Test void emptyDatabaseEnforcesSignupAndAfterOnlyNotifications() {
        var current = flyway("6");
        current.migrate();
        current.validate();
        assertThat(current.migrate().migrationsExecuted).isZero();
        user(1L, "female", 2012); // 2026 in Seoul, exactly 14 by the signup-year rule.
        assertThatThrownBy(() -> user(2L, "male", 2013)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> user(3L, "male", 1899)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> user(4L, null, 2000)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> user(5L, "male", null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns WHERE table_schema=current_schema()
                  AND ((table_name='before_logs' AND column_name='expected_score_comment')
                    OR (table_name='after_logs' AND column_name='actual_score_comment')
                    OR (table_name='notifications' AND column_name IN ('trigger_keyword','trigger_at')))
                """, Integer.class)).isZero();
    }

    @Test void upgradingV5PreservesRecordsAfterRemindersAndTheirDeliveries() {
        flyway("5").migrate();
        user(1L, "female", 2000);
        jdbc.update("""
                INSERT INTO before_logs(id,user_id,situation_type_id,scheduled_at,expected_score,expected_score_comment,
                    worst_scenario,created_at,updated_at) VALUES(2,1,1,'2026-01-01',85,'옛 문구','원문',now(),now())
                """);
        jdbc.update("""
                INSERT INTO after_logs(id,before_log_id,free_writing,actual_score_comment,record_status,created_at,updated_at)
                VALUES(3,2,'내 생각','옛 문구','DRAFT',now(),now())
                """);
        jdbc.update("""
                INSERT INTO notifications(id,user_id,type,priority,before_log_id,trigger_keyword,trigger_at,
                    scheduled_at,dedup_key,status,created_at,updated_at) VALUES
                (4,1,'after_induce',1,2,NULL,NULL,now(),'after:2','shown',now(),now()),
                (5,1,'before_induce',1,NULL,'발표','2026-01-01',now(),'before:발표:2026-01-01','pending',now(),now())
                """);
        jdbc.update("""
                INSERT INTO notification_deliveries(notification_id,channel,status,created_at,updated_at)
                VALUES(4,'push','sent',now(),now()),(5,'push','sent',now(),now())
                """);
        var current = flyway("6");
        current.migrate();
        current.validate();
        assertThat(jdbc.queryForObject("SELECT worst_scenario FROM before_logs WHERE id=2", String.class)).isEqualTo("원문");
        assertThat(jdbc.queryForObject("SELECT free_writing FROM after_logs WHERE id=3", String.class)).isEqualTo("내 생각");
        assertThat(jdbc.queryForList("SELECT notification_id FROM notification_deliveries", Long.class)).containsExactly(4L);
        assertThat(jdbc.queryForObject("SELECT status FROM notifications WHERE id=4", String.class)).isEqualTo("shown");
        assertThatThrownBy(() -> jdbc.update("UPDATE notifications SET type='before_induce' WHERE id=4"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE notifications SET before_log_id=NULL WHERE id=4"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void incompleteLegacyProfileIsRecoverableWithoutInventingPersonalData() {
        flyway("5").migrate();
        user(1L, null, null);
        var current = flyway("6");
        assertThatThrownBy(current::migrate).isInstanceOf(FlywayException.class)
                .hasStackTraceContaining("verified gender and eligible birth_year");
        assertThat(current.info().current().getVersion().toString()).isEqualTo("5");
        assertThat(jdbc.queryForObject("SELECT gender IS NULL AND birth_year IS NULL FROM users WHERE id=1", Boolean.class)).isTrue();
        jdbc.update("UPDATE users SET gender='female',birth_year=2000 WHERE id=1");
        current.migrate();
        assertThat(current.info().current().getVersion().toString()).isEqualTo("6");
    }

    @Test void v5CatalogUpgradeKeepsExistingRecordReferencesAndCommonCardScope() {
        flyway("5").migrate();
        user(1L, "female", 2000);
        jdbc.update("""
                INSERT INTO before_logs(id,user_id,situation_type_id,scheduled_at,expected_score,worst_scenario,created_at,updated_at)
                VALUES(2,1,1,'2026-01-01',85,'기록',now(),now())
                """);
        jdbc.update("""
                INSERT INTO after_logs(id,before_log_id,free_writing,record_status,created_at,updated_at)
                VALUES(3,2,'기존 기록','DRAFT',now(),now())
                """);
        jdbc.update("""
                INSERT INTO distortion_tags(id,name,definition,example_thoughts,self_questions,reframes,display_order,
                    active,created_at,updated_at) VALUES
                    (1,'기존 유형','별도 유형','[]','[]','[]',20,true,now(),now()),
                    (600,'마음 읽기','옛 정의','[]','[]','[]',21,false,now(),now())
                """);
        jdbc.update("""
                INSERT INTO after_distortions(after_log_id,distortion_tag_id,evidence,explanation,created_at)
                VALUES(3,600,'기존 기록','기존 분석',now())
                """);
        jdbc.update("""
                INSERT INTO positive_solutions(id,situation_type_id,category_code,category_name,title,solution_content,
                    display_order,active,created_at,updated_at) VALUES
                    (81,NULL,'resilience','회복력','다시 일어서는 힘','옛 공통 문구',20,false,now(),now()),
                    (82,1,'resilience','회복력','다시 일어서는 힘','상황 한정 문구',21,true,now(),now())
                """);
        var current = flyway("8");
        current.migrate();
        current.validate();
        assertThat(current.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT t.id FROM after_distortions a JOIN distortion_tags t ON t.id=a.distortion_tag_id
                WHERE a.after_log_id=3 AND t.name='마음 읽기' AND t.display_order=6 AND NOT t.active
                    AND jsonb_array_length(t.self_questions)>0
                """, Long.class)).isEqualTo(600L);
        assertThat(jdbc.queryForObject("SELECT name FROM distortion_tags WHERE id=1", String.class)).isEqualTo("기존 유형");
        assertThat(jdbc.queryForObject("SELECT id FROM distortion_tags WHERE name='개인화'", Long.class)).isGreaterThan(600L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM positive_solutions WHERE situation_type_id IS NULL", Integer.class))
                .isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT solution_content FROM positive_solutions WHERE id=81 AND NOT active", String.class))
                .isEqualTo("잠깐 흔들려도 금방 돌아올 수 있어요");
        assertThat(jdbc.queryForObject("SELECT solution_content FROM positive_solutions WHERE id=82 AND situation_type_id=1", String.class))
                .isEqualTo("상황 한정 문구");
    }

    @Test void confirmedCopyUpgradesAV7GuideWithoutChangingItsIdOrVisibility() {
        flyway("7").migrate();
        jdbc.update("UPDATE distortion_tags SET id=901, active=false WHERE name='파국화'");
        var otherCopy = jdbc.queryForObject(
                "SELECT example_thoughts->>0 FROM distortion_tags WHERE name='긍정적 측면 묵살'", String.class);
        var current = flyway("8");
        assertThat(current.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT example_thoughts->>0 FROM distortion_tags WHERE id=901 AND NOT active
                    AND name='파국화' AND jsonb_array_length(self_questions)=1 AND jsonb_array_length(reframes)=1
                """, String.class)).isEqualTo("발표에서 한 번 말이 막히면 발표를 전부 망칠 거야.").isNotEqualTo(otherCopy);
        assertThat(jdbc.queryForObject(
                "SELECT example_thoughts->>0 FROM distortion_tags WHERE name='긍정적 측면 묵살'", String.class)).isEqualTo(otherCopy);
        assertThat(current.migrate().migrationsExecuted).isZero();
        current.validate();
    }
}
