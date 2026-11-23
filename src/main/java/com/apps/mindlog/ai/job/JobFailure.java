package com.apps.mindlog.ai.job;

/** Safe polling reason; never persist a provider error or exception message. */
public enum JobFailure {
    PROVIDER_UNAVAILABLE(true), INVALID_OUTPUT(false), STALE_INPUT(false), RATE_LIMITED(true);
    private final boolean retryable;
    JobFailure(boolean retryable) { this.retryable = retryable; }
    public boolean retryable() { return retryable; }
}
