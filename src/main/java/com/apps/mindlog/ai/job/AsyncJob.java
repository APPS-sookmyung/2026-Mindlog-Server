package com.apps.mindlog.ai.job;

import java.time.Instant;
import java.util.UUID;

/** Internal snapshot. Never return inputSnapshot directly from an API or log it. */
public record AsyncJob(UUID id, long userId, Long beforeLogId, Long afterLogId,
        JobKind kind, JobStatus status, UUID requestKey, long inputVersion,
        long dataGeneration, String inputSnapshot, String result, String failureReason,
        boolean retryable, Instant expiresAt, Instant completedAt, Instant createdAt,
        UUID attemptToken, Instant leaseUntil) {
    public boolean expired(Instant now) { return expiresAt != null && !now.isBefore(expiresAt); }
    @Override public String toString() { return "AsyncJob[id=" + id + ", kind=" + kind + ", status=" + status + "]"; }
}
