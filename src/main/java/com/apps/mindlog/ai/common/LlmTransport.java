package com.apps.mindlog.ai.common;

/**
 * Provider adapter uses RestClient and the selected model's structured-output contract.
 * Enforce request.timeout across the whole call, bound response bytes before buffering,
 * disable body/key logging, and return only the structured output JSON object.
 * Credentials must come from runtime secret configuration. Never register a fake production adapter.
 */
@FunctionalInterface
public interface LlmTransport {
    String complete(LlmClient.Request<?> request);
}
