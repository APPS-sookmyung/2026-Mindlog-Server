package com.apps.mindlog.ai.job;

import com.apps.mindlog.global.config.TimeConfig;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/** Only public fields. AsyncJob itself must never be returned by a controller. */
public final class JobResponse {
    private JobResponse() {}
    public record Accepted(UUID jobId, JobStatus status, long inputVersion) {}
    public record AfterAccepted(UUID jobId, Long afterLogId, JobStatus status, long inputVersion) {}
    public record Failure(String reason, boolean retryable) {}
    public record Poll(UUID jobId, JobStatus status, long inputVersion, OffsetDateTime completedAt,
                       JsonNode result, Failure failure) {}
    public record DraftPoll(UUID jobId, JobStatus status, long inputVersion, JsonNode draft, Failure failure) {}

    public static <T> ResponseEntity<T> accepted(T body) {
        return ResponseEntity.accepted().header("Retry-After","2").body(body);
    }
    static Poll poll(AsyncJob job, JsonNode result) {
        return new Poll(job.id(),job.status(),job.inputVersion(),job.completedAt()==null?null:
                job.completedAt().atZone(TimeConfig.SEOUL).toOffsetDateTime(),
                job.status()==JobStatus.COMPLETED?result:null,
                job.status()==JobStatus.FAILED?new Failure(job.failureReason(),job.retryable()):null);
    }
}
