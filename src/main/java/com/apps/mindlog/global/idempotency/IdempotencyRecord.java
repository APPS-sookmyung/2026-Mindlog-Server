package com.apps.mindlog.global.idempotency;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Exact successful HTTP result; only stable, explicitly allowed response headers are retained. */
public record IdempotencyRecord(int status, String body, Map<String, String> headers) {
    public IdempotencyRecord {
        if (status < 200 || status > 299) throw new IllegalArgumentException("Only successful results may be saved");
        Objects.requireNonNull(body);
        headers = Map.copyOf(headers);
        if (!Set.of("Content-Type", "Location", "Retry-After").containsAll(headers.keySet())) {
            throw new IllegalArgumentException("Unsupported replay header");
        }
    }

    public IdempotencyRecord(int status, String body) {
        this(status, body, Map.of("Content-Type", "application/json"));
    }
}
