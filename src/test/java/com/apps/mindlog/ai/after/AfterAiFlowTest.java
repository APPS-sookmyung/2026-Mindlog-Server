package com.apps.mindlog.ai.after;

import com.apps.mindlog.after.AfterTestAdapters;
import com.apps.mindlog.ai.job.JobExecutor;
import com.apps.mindlog.ai.job.JobStore;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Default: real HTTP adapter with a local provider stub. Live calls require explicit environment opt-in. */
@SpringBootTest(properties = {"mindlog.ai.provider=openai", "mindlog.ai.max-requests-per-hour=20", "mindlog.ai.dispatch-enabled=false"})
@AutoConfigureMockMvc
@Import(AfterTestAdapters.class)
class AfterAiFlowTest {
    private static final boolean LIVE = "true".equalsIgnoreCase(System.getenv("MINDLOG_LIVE_LLM_TEST"));
    private static final AtomicInteger PROVIDER_CALLS = new AtomicInteger();
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpServer PROVIDER = LIVE ? null : startProvider();
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JobExecutor executor;
    @Autowired JobStore jobs;

    @DynamicPropertySource static void configure(DynamicPropertyRegistry registry) {
        registry.add("mindlog.ai.openai.api-key", () -> LIVE ? required("OPENAI_API_KEY") : "test-key");
        registry.add("mindlog.ai.openai.model", () -> LIVE ? required("LLM_MODEL") : "test-model");
        registry.add("mindlog.ai.openai.base-url", () -> LIVE ? "https://api.openai.com/v1/" :
                "http://127.0.0.1:" + PROVIDER.getAddress().getPort() + "/v1/");
    }
    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " must be configured for live verification");
        return value;
    }
    private static HttpServer startProvider() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/responses", exchange -> {
                try (exchange) {
                    PROVIDER_CALLS.incrementAndGet();
                    var request = JSON.readTree(exchange.getRequestBody().readAllBytes());
                    var fields = request.path("text").path("format").path("schema").path("properties");
                    Object result;
                    if (fields.has("summary")) result = Map.of("summary", "걱정과 실제 상황을 구분해 봐요", "patternCount", 1,
                            "distortions", List.of(Map.of("distortionTagId", 6, "distortionTagName", "마음 읽기",
                                    "evidence", "모두 나를 싫어할 것이다", "explanation", "다른 사람의 생각을 미리 짐작하고 있어요")),
                            "analysisCards", List.of(new com.apps.mindlog.after.dto.response.AnalysisResult.Card(
                                    "회복력", null, "잠깐 멈춰도 괜찮아요", "말이 막혔던 순간과 이후를 나누어 돌아봐요")));
                    else if (fields.has("content")) result = Map.of("content", "발표에서 말이 막혔다. 모두 나를 싫어할 거라는 걱정이 들었다.");
                    else result = Map.of("question", "잠깐 말이 막혔던 순간 이후에는 어떤 일이 있었나요?");
                    String body = JSON.writeValueAsString(Map.of("status", "completed", "output", List.of(
                            Map.of("type", "message", "role", "assistant", "status", "completed", "content", List.of(
                                    Map.of("type", "output_text", "text", JSON.writeValueAsString(result)))))));
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                }
            });
            server.start();
            return server;
        } catch (java.io.IOException failure) { throw new IllegalStateException("Could not start local test provider", failure); }
    }
    @AfterAll static void stopProvider() { if (PROVIDER != null) PROVIDER.stop(0); }

    @BeforeEach void setup() {
        cleanup(); PROVIDER_CALLS.set(0);
        jdbc.update("""
                INSERT INTO users(id,email,nickname,representative_character_id,gender,birth_year,account_status,
                  onboarding_completed,after_reminder_enabled,weekly_report_start_day,created_at,updated_at)
                VALUES(980,'flow@example.test','테스트 사용자',1,'female',2000,'active',true,true,'sun',now(),now())
                """);
        jdbc.update("""
                INSERT INTO before_logs(id,user_id,situation_type_id,title,scheduled_at,expected_score,worst_scenario,created_at,updated_at)
                VALUES(980,980,1,'테스트 발표','2026-11-23',80,'발표에서 말이 막힐까 걱정돼요',now(),now())
                """);
    }
    @AfterEach void cleanup() { jdbc.update("DELETE FROM users WHERE id=980"); }
    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request) {
        return request.with(user("980")).header("X-API-Version", "1").contentType("application/json");
    }
    private String analysis(long after) throws Exception {
        return mvc.perform(authenticated(post("/api/after-logs/" + after + "/analyses"))
                        .header("Idempotency-Key", "analysis-once").content("{\"inputVersion\":1}"))
                .andExpect(status().isAccepted()).andExpect(header().string("Retry-After", "2"))
                .andReturn().getResponse().getContentAsString();
    }
    private static UUID jobId(String response) { return UUID.fromString(JSON.readTree(response).path("jobId").asText()); }

    @Test void fullFlowThroughHttpTransportReplaysRequestsAndPersistsInsightOnce() throws Exception {
        String created = mvc.perform(authenticated(post("/api/after-logs"))
                        .content("{\"beforeLogId\":980,\"freeWriting\":\"발표에서 말이 막혀서 모두 나를 싫어할 것이다\",\"bodySymptomIds\":[1]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long after = JSON.readTree(created).path("id").longValue();
        String first;
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> analysis(after)); var b = pool.submit(() -> analysis(after));
            first = a.get(30, TimeUnit.SECONDS);
            assertThat(b.get(30, TimeUnit.SECONDS)).isEqualTo(first);
        }
        UUID analysisId = jobId(first);
        executor.run(analysisId); executor.run(analysisId);
        mvc.perform(authenticated(get("/api/after-logs/" + after + "/analyses/" + analysisId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        mvc.perform(authenticated(get("/api/after-logs/" + after + "/analysis")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.analysisStatus").value("COMPLETED"));
        assertThat(analysis(after)).isEqualTo(first);

        String draft = mvc.perform(authenticated(post("/api/after-logs/" + after + "/diary-drafts"))
                        .header("Idempotency-Key", "draft-once").content("{\"inputVersion\":1}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        UUID draftId = jobId(draft); executor.run(draftId);
        String polled = mvc.perform(authenticated(get("/api/after-logs/" + after + "/diary-drafts/" + draftId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"))
                .andReturn().getResponse().getContentAsString();
        String diary = JSON.readTree(polled).path("draft").path("content").asText();
        String payload = JSON.writeValueAsString(Map.of("inputVersion", 1, "finalDiary", diary, "actualScore", 0));
        String confirmed = null;
        for (int i = 0; i < 2; i++) {
            String result = mvc.perform(authenticated(put("/api/after-logs/" + after + "/final-diary"))
                            .header("Idempotency-Key", "final-once").content(payload))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.actualScore").value(0))
                    .andReturn().getResponse().getContentAsString();
            if (confirmed == null) confirmed = result; else assertThat(result).isEqualTo(confirmed);
        }
        var insightIds = jdbc.queryForList("SELECT id FROM ai_jobs WHERE after_log_id=? AND kind='record_insight'", UUID.class, after);
        assertThat(insightIds).hasSize(1);
        executor.run(insightIds.getFirst()); executor.run(insightIds.getFirst());
        assertThat(jobs.find(insightIds.getFirst()).orElseThrow().status().name()).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_insights WHERE after_log_id=?", Integer.class, after)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT used FROM llm_quota_usage WHERE user_id=980", Integer.class)).isEqualTo(3);
        if (!LIVE) assertThat(PROVIDER_CALLS.get()).isEqualTo(3);
    }
}
