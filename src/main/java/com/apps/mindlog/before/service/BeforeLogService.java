package com.apps.mindlog.before.service;

import com.apps.mindlog.before.dto.request.CreateBeforeRequest;
import com.apps.mindlog.before.dto.response.BeforeCreatedResponse;
import com.apps.mindlog.before.entity.BeforeLog;
import com.apps.mindlog.before.repository.BeforeLogRepository;
import com.apps.mindlog.ai.job.JobKind;
import com.apps.mindlog.ai.job.JobStatus;
import com.apps.mindlog.ai.job.JobStore;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.ObjectMapper;

@Service
public class BeforeLogService {
    private final BeforeLogRepository logs;
    private final ReferenceValidator references;
    private final BeforeLogAccess access;
    private final EntityManager entityManager;
    private final Clock clock;
    private final JobStore jobs;
    private final IdempotencyService idempotency;
    private final ObjectMapper json;

    public BeforeLogService(BeforeLogRepository logs, ReferenceValidator references,
            BeforeLogAccess access, EntityManager entityManager, Clock clock, JobStore jobs,
            IdempotencyService idempotency, ObjectMapper json) {
        this.logs = logs; this.references = references; this.access = access;
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
        if (job.inputVersion() != request.inputVersion()) throw new ApiException(ErrorType.STALE_INPUT_VERSION);
        User user = entityManager.getReference(User.class, account.id());
        BeforeLog saved = logs.saveAndFlush(BeforeLog.create(user, situation, request.scheduledAt(),
                request.expectedScore(), request.worstScenario().strip(), request.title(), request.description()));
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
