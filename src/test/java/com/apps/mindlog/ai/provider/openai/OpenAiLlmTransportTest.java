package com.apps.mindlog.ai.provider.openai;

import com.apps.mindlog.ai.after.DiaryDraftResult;
import com.apps.mindlog.ai.common.InvalidLlmOutputException;
import com.apps.mindlog.ai.common.LlmClient;
import com.apps.mindlog.ai.common.LlmUnavailableException;
import com.apps.mindlog.ai.common.ValidatingLlmClient;
import com.apps.mindlog.ai.job.JobKind;
import com.sun.net.httpserver.HttpServer;
import jakarta.validation.Validation;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class OpenAiLlmTransportTest {
    private HttpServer server;
    private final JsonMapper json = JsonMapper.builder().build();
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<JsonNode> received = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String response;
    private volatile long headerDelay, bodyDelay;
    private volatile boolean chunked;
    private java.util.concurrent.ExecutorService workers;

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        workers = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(workers);
        response = success("{\"content\":\"테스트 일기\"}");
        server.createContext("/v1/responses", exchange -> {
            try (exchange) {
                calls.incrementAndGet();
                authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                received.set(json.readTree(exchange.getRequestBody().readAllBytes()));
                pause(headerDelay);
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, chunked ? 0 : bytes.length);
                if (bodyDelay > 0) {
                    exchange.getResponseBody().write(bytes, 0, 1);
                    exchange.getResponseBody().flush();
                    pause(bodyDelay);
                    exchange.getResponseBody().write(bytes, 1, bytes.length - 1);
                } else exchange.getResponseBody().write(bytes);
            } catch (java.io.IOException closedAfterTimeout) { /* Expected when the client cancels. */ }
        });
        server.start();
    }
    @AfterEach void stop() { server.stop(0); workers.shutdownNow(); }
    private static void pause(long millis) {
        try { Thread.sleep(millis); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
    }
    private OpenAiProperties properties() {
        var p = new OpenAiProperties();
        p.setApiKey("test-secret"); p.setModel("configured-model");
        p.setBaseUrl(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/"));
        return p;
    }
    private LlmClient.Request<DiaryDraftResult> request(Duration timeout) {
        return new LlmClient.Request<>(JobKind.DIARY_DRAFT, "system instruction", "{\"writing\":\"private-input\"}", DiaryDraftResult.class, timeout);
    }
    private String success(String text) {
        return json.writeValueAsString(Map.of("status", "completed", "output", List.of(
                Map.of("type", "reasoning", "summary", List.of()),
                Map.of("type", "message", "role", "assistant", "status", "completed", "content",
                        List.of(Map.of("type", "output_text", "text", text))))));
    }

    @Test void sendsConfiguredModelStrictSchemaAndSeparateUntrustedInputWithoutStorage() {
        try (var transport = new OpenAiLlmTransport(properties()); var factory = Validation.buildDefaultValidatorFactory()) {
            var client = new ValidatingLlmClient(transport, factory.getValidator(), "openai/configured-model");
            assertThat(client.generate(request(Duration.ofSeconds(5))).content()).isEqualTo("테스트 일기");
            var sent = received.get();
            assertThat(authorization.get()).isEqualTo("Bearer test-secret");
            assertThat(sent.path("model").asText()).isEqualTo("configured-model");
            assertThat(sent.path("store").booleanValue()).isFalse();
            assertThat(sent.path("text").path("format").path("strict").booleanValue()).isTrue();
            assertThat(sent.path("input").get(0).path("role").asText()).isEqualTo("system");
            assertThat(sent.path("input").get(1).path("content").asText()).contains("private-input");
            assertThat(properties().toString()).doesNotContain("test-secret");
        }
    }

    @Test void providerHttpFailuresAreRetryableAndDoNotLeakOrAutomaticallyRetry() {
        try (var transport = new OpenAiLlmTransport(properties())) {
            for (int code : new int[]{401, 429, 500, 503}) {
                status = code; response = "provider private body and test-secret";
                int before = calls.get();
                assertThatThrownBy(() -> transport.complete(request(Duration.ofSeconds(5))))
                        .isInstanceOf(LlmUnavailableException.class).hasMessage("AI provider unavailable").hasNoCause();
                assertThat(calls.get()).isEqualTo(before + 1);
            }
        }
    }

    @Test void wholeCallDeadlineAlsoBoundsAStalledBody() {
        try (var transport = new OpenAiLlmTransport(properties())) {
            for (boolean stallBody : new boolean[]{false, true}) {
                headerDelay = stallBody ? 0 : 2000; bodyDelay = stallBody ? 2000 : 0;
                long started = System.nanoTime();
                assertThatThrownBy(() -> transport.complete(request(Duration.ofMillis(200))))
                        .isInstanceOf(LlmUnavailableException.class);
                assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(1));
            }
        }
    }

    @Test void oversizedAndMalformedProviderEnvelopesAreNotMisclassifiedAsModelOutput() {
        var p = properties(); p.setMaxResponseBytes(1024);
        try (var transport = new OpenAiLlmTransport(p)) {
            for (boolean stream : new boolean[]{false, true}) {
                chunked = stream; response = success("가".repeat(2000));
                assertThatThrownBy(() -> transport.complete(request(Duration.ofSeconds(5))))
                        .isInstanceOf(LlmUnavailableException.class);
            }
            chunked = false;
            for (String malformed : List.of("not JSON", "[]", "{}", "{\"status\":\"incomplete\"}")) {
                response = malformed;
                assertThatThrownBy(() -> transport.complete(request(Duration.ofSeconds(5))))
                        .isInstanceOf(LlmUnavailableException.class).hasNoCause();
            }
        }
    }

    @Test void refusalAndMalformedStructuredContentAreInvalidOutput() {
        try (var transport = new OpenAiLlmTransport(properties()); var factory = Validation.buildDefaultValidatorFactory()) {
            response = "{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"status\":\"completed\",\"role\":\"assistant\",\"content\":[{\"type\":\"refusal\",\"refusal\":\"private reason\"}]}]}";
            assertThatThrownBy(() -> transport.complete(request(Duration.ofSeconds(5))))
                    .isInstanceOf(InvalidLlmOutputException.class).hasNoCause();
            response = success("{\"content\":\" \"}");
            var client = new ValidatingLlmClient(transport, factory.getValidator(), "test");
            assertThatThrownBy(() -> client.generate(request(Duration.ofSeconds(5))))
                    .isInstanceOf(InvalidLlmOutputException.class);
        }
    }

    @Test void rejectsUnconfiguredModelsAndNonLocalPlaintextEndpoints() {
        var p = properties(); p.setModel("");
        assertThatIllegalArgumentException().isThrownBy(() -> new OpenAiLlmTransport(p));
        p.setModel("model"); p.setBaseUrl(URI.create("http://example.com/v1/"));
        assertThatIllegalArgumentException().isThrownBy(() -> new OpenAiLlmTransport(p));
    }
}
