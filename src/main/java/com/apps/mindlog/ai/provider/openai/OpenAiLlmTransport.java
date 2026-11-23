package com.apps.mindlog.ai.provider.openai;

import com.apps.mindlog.ai.common.InvalidLlmOutputException;
import com.apps.mindlog.ai.common.LlmClient;
import com.apps.mindlog.ai.common.LlmTransport;
import com.apps.mindlog.ai.common.LlmUnavailableException;
import com.apps.mindlog.ai.common.RecordJsonSchema;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** OpenAI Responses wire format stays here; only a structured JSON object leaves the adapter. */
public final class OpenAiLlmTransport implements LlmTransport, AutoCloseable {
    private final HttpClient http;
    private final URI endpoint;
    private final String apiKey;
    private final String model;
    private final Duration timeout;
    private final int maxOutputTokens;
    private final int maxResponseBytes;
    private final JsonMapper json = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    public OpenAiLlmTransport(OpenAiProperties properties) {
        properties.validate();
        apiKey = properties.getApiKey();
        model = properties.getModel();
        timeout = properties.getTimeout();
        maxOutputTokens = properties.getMaxOutputTokens();
        maxResponseBytes = properties.getMaxResponseBytes();
        String base = properties.getBaseUrl().toString();
        endpoint = URI.create(base.endsWith("/") ? base + "responses" : base + "/responses");
        http = HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Override public String complete(LlmClient.Request<?> request) {
        Duration deadline = request.timeout().compareTo(timeout) < 0 ? request.timeout() : timeout;
        var activeResponse = new AtomicReference<ClientHttpResponse>();
        var task = new FutureTask<>(() -> exchange(request, deadline, activeResponse));
        Thread.ofVirtual().name("openai-call").start(task);
        try {
            return task.get(deadline.toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new LlmUnavailableException();
        } catch (TimeoutException timedOut) {
            throw new LlmUnavailableException();
        } catch (ExecutionException failed) {
            if (failed.getCause() instanceof InvalidLlmOutputException) throw new InvalidLlmOutputException();
            throw new LlmUnavailableException();
        } finally {
            task.cancel(true);
            var response = activeResponse.getAndSet(null);
            if (response != null) response.close();
        }
    }

    private String exchange(LlmClient.Request<?> request, Duration deadline,
                            AtomicReference<ClientHttpResponse> activeResponse) {
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(deadline);
        // A dedicated builder avoids application-wide body logging interceptors and automatic retries.
        var client = RestClient.builder().requestFactory(factory).build();
        var body = Map.of("model", model, "store", false, "stream", false,
                "max_output_tokens", maxOutputTokens,
                "input", List.of(Map.of("role", "system", "content", request.systemPrompt()),
                        Map.of("role", "user", "content", request.inputJson())),
                "text", Map.of("format", Map.of("type", "json_schema", "name", "mindlog_result",
                        "strict", true, "schema", RecordJsonSchema.forRecord(request.resultType()))));
        return client.post().uri(endpoint).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth(apiKey)).body(json.writeValueAsString(body))
                .exchange((httpRequest, response) -> {
                    activeResponse.set(response);
                    try {
                        if (Thread.currentThread().isInterrupted() || !response.getStatusCode().is2xxSuccessful())
                            throw new LlmUnavailableException();
                        // Do not buffer provider error bodies. Bound success bodies before JSON parsing.
                        if (response.getHeaders().getContentLength() > maxResponseBytes)
                            throw new LlmUnavailableException();
                        byte[] bytes = response.getBody().readNBytes(maxResponseBytes + 1);
                        if (bytes.length > maxResponseBytes) throw new LlmUnavailableException();
                        return extract(json.readTree(bytes));
                    } finally {
                        activeResponse.compareAndSet(response, null);
                    }
                });
    }

    private String extract(JsonNode response) {
        if (response == null || !response.isObject()) throw new LlmUnavailableException();
        if (!"completed".equals(response.path("status").asText())) {
            if ("content_filter".equals(response.path("incomplete_details").path("reason").asText()))
                throw new InvalidLlmOutputException();
            throw new LlmUnavailableException();
        }
        String result = null;
        var output = response.path("output");
        if (!output.isArray()) throw new LlmUnavailableException();
        for (var item : output) {
            if (!"message".equals(item.path("type").asText())) continue;
            if (!"assistant".equals(item.path("role").asText())
                    || !"completed".equals(item.path("status").asText())) throw new LlmUnavailableException();
            // Models may emit commentary messages before their final answer.
            String phase = item.path("phase").asText("");
            if (!phase.isEmpty() && !"final_answer".equals(phase)) continue;
            var content = item.path("content");
            if (!content.isArray()) throw new LlmUnavailableException();
            for (var part : content) {
                if ("refusal".equals(part.path("type").asText())) throw new InvalidLlmOutputException();
                if (!"output_text".equals(part.path("type").asText()) || !part.path("text").isString())
                    throw new LlmUnavailableException();
                if (result != null) throw new InvalidLlmOutputException();
                result = part.path("text").asText();
            }
        }
        if (result == null || result.isBlank()) throw new InvalidLlmOutputException();
        return result;
    }

    @Override public void close() { http.shutdownNow(); }
}
