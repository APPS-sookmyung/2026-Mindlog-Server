package com.apps.mindlog.global.error;

import java.sql.BatchUpdateException;
import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ConstraintViolationMapperTest {
    @ParameterizedTest
    @CsvSource({
            "ux_users_email_lower, EMAIL_ALREADY_EXISTS",
            "ux_after_logs_before_log_id, AFTER_LOG_ALREADY_EXISTS",
            "ux_notifications_user_dedup, NOTIFICATION_ALREADY_SCHEDULED",
            "ux_ai_jobs_active_after_kind, ANALYSIS_IN_PROGRESS"
    })
    void mapsPostgresMetadataThroughSpringWrapping(String constraint, ErrorType expected) {
        var exception = new DataIntegrityViolationException("private SQL", postgres("23505", constraint));
        assertThat(ConstraintViolationMapper.map(exception)).get()
                .extracting(ConstraintViolationMapper.Conflict::errorType).isEqualTo(expected);
    }

    @Test
    void mapsHibernateMetadataWithoutMessageParsing() {
        var exception = new ConstraintViolationException("private", new SQLException("private", "23505"),
                "insert private", "ux_after_logs_before_log_id");
        assertThat(ConstraintViolationMapper.map(exception)).get()
                .extracting(ConstraintViolationMapper.Conflict::errorType).isEqualTo(ErrorType.AFTER_LOG_ALREADY_EXISTS);
    }

    @ParameterizedTest
    @CsvSource({"23503,ux_users_email_lower", "23514,ux_users_email_lower", "23502,ux_users_email_lower",
            "23505,unknown_constraint", "23505,ux_af_ai_feedbacks_after_log_id", "23505,ux_ai_jobs_request"})
    void leavesUnspecifiedOrNonUniqueFailuresUnmapped(String state, String constraint) {
        assertThat(ConstraintViolationMapper.map(postgres(state, constraint))).isEmpty();
    }

    @Test
    void ignoresMessageHintsAndMissingMetadata() {
        assertThat(ConstraintViolationMapper.map(new SQLException("23505 ux_users_email_lower", "23505"))).isEmpty();
        assertThat(ConstraintViolationMapper.map(new RuntimeException("ux_users_email_lower"))).isEmpty();
        assertThat(ConstraintViolationMapper.map(postgres("23505", null))).isEmpty();
        assertThat(ConstraintViolationMapper.map(null)).isEmpty();
    }

    @Test
    void followsBatchExceptionsAndStopsOnCycles() {
        var batch = new BatchUpdateException();
        batch.setNextException(postgres("23505", "ux_users_email_lower"));
        assertThat(ConstraintViolationMapper.map(batch)).isPresent();
        var first = new SQLException("private", "23503");
        var second = new SQLException("private", "23514");
        first.initCause(second);
        second.initCause(first);
        assertThat(ConstraintViolationMapper.map(first)).isEmpty();
        var cyclicBatch = new SQLException();
        cyclicBatch.setNextException(cyclicBatch);
        assertThat(ConstraintViolationMapper.map(cyclicBatch)).isEmpty();
    }

    @Test
    void rendersSafeProblemDetailsForKnownConflict() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new FailingController(
                new DataIntegrityViolationException("private SQL", postgres("23505", "ux_users_email_lower"))))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/test/conflict").queryParam("secret", "private"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(ErrorType.EMAIL_ALREADY_EXISTS.getType().toString()))
                .andExpect(jsonPath("$.instance").value("/test/conflict"))
                .andExpect(jsonPath("$.detail").value("이미 가입된 이메일입니다."))
                .andExpect(content().string(not(containsString("private"))))
                .andExpect(content().string(not(containsString("ux_users"))));
    }

    @Test
    void unknownConstraintStillReturnsSafeInternalError() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new FailingController(
                new DataIntegrityViolationException("private", postgres("23505", "unknown_constraint"))))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/test/conflict"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.type").value(ErrorType.INTERNAL_ERROR.getType().toString()))
                .andExpect(content().string(not(containsString("private"))));
    }

    private PSQLException postgres(String state, String constraint) {
        return new PSQLException(new ServerErrorMessage("SERROR\0C" + state
                + "\0Mprivate value\0Dprivate diary\0" + (constraint == null ? "" : "n" + constraint + "\0") + "\0"));
    }

    @RestController
    static class FailingController {
        private final RuntimeException failure;
        FailingController(RuntimeException failure) { this.failure = failure; }
        @GetMapping("/test/conflict")
        void fail() { throw failure; }
    }
}
