package com.apps.mindlog.global.error;

import java.sql.DriverManager;
import java.sql.SQLException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers
class ConstraintViolationPostgresTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @ParameterizedTest
    @CsvSource({
            "ux_users_email_lower,EMAIL_ALREADY_EXISTS",
            "ux_after_logs_before_log_id,AFTER_LOG_ALREADY_EXISTS",
            "ux_notifications_user_dedup,NOTIFICATION_ALREADY_SCHEDULED",
            "ux_ai_jobs_active_after_kind,ANALYSIS_IN_PROGRESS"
    })
    void mapsRealServerConstraintMetadata(String constraint, ErrorType expected) throws Exception {
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("CREATE TEMP TABLE conflict_fixture (value text)");
            statement.execute("CREATE UNIQUE INDEX " + constraint + " ON conflict_fixture(lower(value))");
            statement.execute("INSERT INTO conflict_fixture VALUES ('Private-Diary')");
            SQLException failure = assertThrows(SQLException.class,
                    () -> statement.execute("INSERT INTO conflict_fixture VALUES ('PRIVATE-DIARY')"));
            assertThat(ConstraintViolationMapper.map(new DataIntegrityViolationException("save failed", failure)))
                    .get().extracting(ConstraintViolationMapper.Conflict::errorType).isEqualTo(expected);
        }
    }
}
