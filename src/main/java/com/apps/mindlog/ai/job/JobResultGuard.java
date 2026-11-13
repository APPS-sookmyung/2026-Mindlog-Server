package com.apps.mindlog.ai.job;

/**
 * Called inside the result transaction, before locking ai_jobs.
 * Lock account first, then target record; verify active owner, dataGeneration,
 * inputVersion and the state required by this job kind. Missing/deleted is false.
 * The locks must remain held until result application commits.
 */
@FunctionalInterface
public interface JobResultGuard {
    boolean lockAndIsCurrent(AsyncJob job);
}
