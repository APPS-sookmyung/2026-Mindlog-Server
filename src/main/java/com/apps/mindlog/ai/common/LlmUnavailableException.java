package com.apps.mindlog.ai.common;

/** Safe, deliberately omits the provider exception cause. */
public class LlmUnavailableException extends RuntimeException {
    public LlmUnavailableException(){super("AI provider unavailable");}
}
