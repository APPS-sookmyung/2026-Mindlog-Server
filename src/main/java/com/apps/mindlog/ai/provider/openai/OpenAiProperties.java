package com.apps.mindlog.ai.provider.openai;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("mindlog.ai.openai")
public class OpenAiProperties {
    private String apiKey = "";
    private String model = "";
    private URI baseUrl = URI.create("https://api.openai.com/v1/");
    private Duration timeout = Duration.ofSeconds(90);
    private Duration connectTimeout = Duration.ofSeconds(10);
    private int maxOutputTokens = 4096;
    private int maxResponseBytes = 1_000_000;

    public void validate() {
        if (apiKey == null || apiKey.isBlank() || apiKey.contains("\r") || apiKey.contains("\n"))
            throw new IllegalArgumentException("OpenAI API key must be configured");
        if (model == null || model.isBlank()) throw new IllegalArgumentException("OpenAI model must be configured");
        if (baseUrl == null || baseUrl.getHost() == null || baseUrl.getUserInfo() != null
                || baseUrl.getQuery() != null || baseUrl.getFragment() != null
                || !("https".equals(baseUrl.getScheme()) || ("http".equals(baseUrl.getScheme())
                && LOOPBACK_HOSTS.contains(baseUrl.getHost()))))
            throw new IllegalArgumentException("OpenAI base URL must use HTTPS (HTTP is only allowed on loopback)");
        if (timeout == null || timeout.isNegative() || timeout.isZero()
                || timeout.compareTo(Duration.ofMinutes(10)) >= 0
                || connectTimeout == null || connectTimeout.isNegative() || connectTimeout.isZero())
            throw new IllegalArgumentException("LLM timeouts must be positive and the call must fit within the job lease");
        if (maxOutputTokens < 1 || maxOutputTokens > 32_768 || maxResponseBytes < 1024 || maxResponseBytes > 1_000_000)
            throw new IllegalArgumentException("Invalid LLM response limit");
    }

    private static final java.util.Set<String> LOOPBACK_HOSTS = java.util.Set.of("localhost", "127.0.0.1", "[::1]");
    public String getApiKey() { return apiKey; }
    public void setApiKey(String value) { apiKey = value; }
    public String getModel() { return model; }
    public void setModel(String value) { model = value; }
    public URI getBaseUrl() { return baseUrl; }
    public void setBaseUrl(URI value) { baseUrl = value; }
    public Duration getTimeout() { return timeout; }
    public void setTimeout(Duration value) { timeout = value; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration value) { connectTimeout = value; }
    public int getMaxOutputTokens() { return maxOutputTokens; }
    public void setMaxOutputTokens(int value) { maxOutputTokens = value; }
    public int getMaxResponseBytes() { return maxResponseBytes; }
    public void setMaxResponseBytes(int value) { maxResponseBytes = value; }
    @Override public String toString() { return "OpenAiProperties[credentials redacted]"; }
}
