package com.apps.mindlog.ai.job;

/** Domain worker. Register only once its real ownership guard and client are available. */
public interface JobHandler extends JobResultGuard {
    JobKind kind();
    default boolean ready() { return true; }
    default void started(AsyncJob job) {}
    default void failed(AsyncJob job, JobFailure reason) {}
    /** External work outside a database transaction. Must finish within the ten-minute lease. */
    String compute(AsyncJob job);
    /** Validate result and apply domain changes in the same transaction as COMPLETED. */
    void apply(AsyncJob job, String result);
}
