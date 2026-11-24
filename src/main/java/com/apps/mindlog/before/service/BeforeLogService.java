package com.apps.mindlog.before.service;

import com.apps.mindlog.before.dto.request.CreateBeforeRequest;
import com.apps.mindlog.before.dto.response.BeforeCreatedResponse;
import com.apps.mindlog.before.dto.response.BeforeLogDetailResponse;
import com.apps.mindlog.before.dto.response.BeforeLogListResponse;
import com.apps.mindlog.before.entity.BeforeLog;
import com.apps.mindlog.before.repository.BeforeLogRepository;
import com.apps.mindlog.ai.job.JobKind;
import com.apps.mindlog.ai.job.JobStatus;
import com.apps.mindlog.ai.job.JobStore;
import com.apps.mindlog.after.repository.AfterLogRepository;
import com.apps.mindlog.global.config.TimeConfig;
import com.apps.mindlog.global.error.ErrorType;
import com.apps.mindlog.global.error.exception.ApiException;
import com.apps.mindlog.global.idempotency.IdempotencyKeyFilter;
import com.apps.mindlog.global.idempotency.IdempotencyRecord;
import com.apps.mindlog.global.idempotency.IdempotencyService;
import com.apps.mindlog.reference.service.ReferenceValidator;
import com.apps.mindlog.user.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.ObjectMapper;

@Service
public class BeforeLogService {
    private final BeforeLogRepository logs;
    private final AfterLogRepository afterLogs;
    private final ReferenceValidator references;
    private final BeforeLogAccess access;
    private final EntityManager entityManager;
    private final Clock clock;
    private final JobStore jobs;
    private final IdempotencyService idempotency;
    private final ObjectMapper json;

    public BeforeLogService(BeforeLogRepository logs, AfterLogRepository afterLogs, ReferenceValidator references,
            BeforeLogAccess access, EntityManager entityManager, Clock clock, JobStore jobs,
            IdempotencyService idempotency, ObjectMapper json) {
        this.logs = logs; this.afterLogs = afterLogs; this.references = references; this.access = access;
        this.entityManager = entityManager; this.clock = clock;
        this.jobs = jobs; this.idempotency = idempotency; this.json = json;
    }

    public ResponseEntity<String> create(CreateBeforeRequest request, HttpServletRequest servletRequest) {
        String key = IdempotencyKeyFilter.requireKey(servletRequest);
        var account = access.current();
        String requestJson = json.writeValueAsString(request);
        var result = idempotency.execute(new IdempotencyService.Scope(account.id(), account.dataGeneration(), "before-log-create"),
                key, requestJson, () -> requireOnboarded(account), () -> createOnce(request, account));
        var response = ResponseEntity.status(result.status());
        result.headers().forEach(response::header);
        return response.body(result.body());
    }

    @Transactional(readOnly = true)
    public BeforeLogDetailResponse getDetail(long beforeLogId) {
        var account = access.current();
        BeforeLog log = logs.findByIdAndUser_Id(beforeLogId, account.id())
                .orElseThrow(() -> new ApiException(ErrorType.RESOURCE_NOT_FOUND));
        java.util.List<?> feedbackRows = entityManager.createNativeQuery("""
                        select feedback_content
                        from bf_ai_feedbacks
                        where before_log_id = ?1
                        """)
                .setParameter(1, beforeLogId)
                .getResultList();
        String feedbackContent = feedbackRows.isEmpty() ? null : (String) feedbackRows.getFirst();
        var rebuttal = feedbackContent == null ? null : new BeforeLogDetailResponse.Rebuttal(feedbackContent);
        var after = afterLogs.findByBeforeLogId(beforeLogId).orElse(null);
        boolean hasAfter = after != null && after.getRecordStatus() == com.apps.mindlog.after.entity.RecordStatus.FINALIZED;
        Long afterLogId = after == null ? null : after.getId();
        return new BeforeLogDetailResponse(log.getId(), log.getSituationType().getId(),
                log.getSituationType().getName(), log.getScheduledAt(), log.getExpectedScore(), log.getWorstScenario(),
                hasAfter, afterLogId, log.getTitle(), log.getDescription(), rebuttal,
                log.getCreatedAt().atZone(TimeConfig.SEOUL).toOffsetDateTime(),
                log.getUpdatedAt().atZone(TimeConfig.SEOUL).toOffsetDateTime());
    }

    @Transactional(readOnly = true)
    public BeforeLogListResponse getList(int page, int size, Boolean hasAfter) {
        if (page < 0 || size < 1) throw new ApiException(ErrorType.VALIDATION_ERROR);
        int appliedSize = Math.min(size, 100);
        var account = access.current();
        var pageable = PageRequest.of(page, appliedSize,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<BeforeLog> result;
        try {
            result = logs.findList(account.id(), hasAfter, pageable);
        } catch (ArithmeticException exception) {
            throw new ApiException(ErrorType.VALIDATION_ERROR);
        }
        var content = result.getContent().stream().map(log -> {
            var after = afterLogs.findByBeforeLogId(log.getId()).orElse(null);
            boolean finalized = after != null && after.getRecordStatus()
                    == com.apps.mindlog.after.entity.RecordStatus.FINALIZED;
            return new BeforeLogListResponse.Item(log.getId(), log.getSituationType().getId(),
                    log.getSituationType().getName(), log.getScheduledAt(), log.getExpectedScore(),
                    log.getWorstScenario(), finalized, after == null ? null : after.getId(),
                    log.getCreatedAt().atZone(TimeConfig.SEOUL).toOffsetDateTime());
        }).toList();
        return new BeforeLogListResponse(content, new BeforeLogListResponse.PageInfo(appliedSize,
                result.getNumber(), result.getTotalElements(), result.getTotalPages()));
    }

    @Transactional
    public IdempotencyRecord createOnce(CreateBeforeRequest request,
            com.apps.mindlog.global.security.AccountAccess.Account account) {
        requireOnboarded(account);
        LocalDate today = LocalDate.now(clock.withZone(TimeConfig.SEOUL));
        if (request.scheduledAt().isBefore(today)) throw new ApiException(ErrorType.VALIDATION_ERROR);
        var situation = references.requireSituation(request.situationTypeId(), ReferenceValidator.SituationContext.BEFORE);
        var job = jobs.find(request.rebuttalJobId()).filter(value -> value.userId() == account.id()
                && value.dataGeneration() == account.dataGeneration() && value.kind() == JobKind.BEFORE_REBUTTAL
                && value.status() == JobStatus.COMPLETED && value.inputVersion() == request.inputVersion()
                && !value.expired(clock.instant())).orElseThrow(() -> new ApiException(ErrorType.ANALYSIS_NOT_READY));
        var input = json.readTree(job.inputSnapshot());
        if (input == null || input.path("situationTypeId").asLong(-1) != request.situationTypeId()
                || !request.scheduledAt().toString().equals(input.path("scheduledAt").asText())
                || input.path("expectedScore").asInt(-1) != request.expectedScore()
                || !request.worstScenario().strip().equals(input.path("worstScenario").asText())) {
            throw new ApiException(ErrorType.STALE_INPUT_VERSION);
        }
        var feedback = job.result() == null ? null : json.readTree(job.result());
        String feedbackContent = feedback == null ? null : feedback.path("feedbackContent").asString(null);
        if (feedbackContent == null || feedbackContent.isBlank()) {
            throw new ApiException(ErrorType.ANALYSIS_NOT_READY);
        }
        User user = entityManager.getReference(User.class, account.id());
        BeforeLog saved = logs.saveAndFlush(BeforeLog.create(user, situation, request.scheduledAt(),
                request.expectedScore(), request.worstScenario().strip(), request.title(), request.description()));
        entityManager.createNativeQuery("""
                insert into bf_ai_feedbacks(before_log_id, feedback_content, similar_records_comment,
                    similar_record_ids, model_version, created_at, updated_at)
                values (?, ?, ?, cast(? as jsonb), ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """)
                .setParameter(1, saved.getId())
                .setParameter(2, feedbackContent)
                .setParameter(3, feedback.path("similarRecordsComment").isNull()
                        ? null : feedback.path("similarRecordsComment").asString())
                .setParameter(4, null)
                .setParameter(5, "before-rebuttal-v1")
                .executeUpdate();
        BeforeCreatedResponse body = new BeforeCreatedResponse(saved.getId(), situation.getId(), situation.getName(), saved.getScheduledAt(),
                saved.getExpectedScore(), saved.getWorstScenario(), saved.getTitle(), saved.getDescription(), false, null,
                saved.getCreatedAt().atZone(TimeConfig.SEOUL).toOffsetDateTime());
        String serialized = json.writeValueAsString(body);
        return new IdempotencyRecord(201, serialized, Map.of("Content-Type", "application/json",
                HttpHeaders.LOCATION, "/api/before-logs/" + saved.getId()));
    }

    private static void requireOnboarded(com.apps.mindlog.global.security.AccountAccess.Account account) {
        if (!account.onboardingCompleted()) throw new ApiException(ErrorType.ONBOARDING_REQUIRED);
    }
}
