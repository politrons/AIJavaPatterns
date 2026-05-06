package com.example.agenticrag.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Minimal Ollama client backed by Java's built-in HTTP client.
 *
 * <p>The client uses Ollama's non-streaming generate endpoint so the demo is
 * easy to follow from a command line.</p>
 */
public final class OllamaLanguageModelClient implements LanguageModelClient {

    public static final String DEFAULT_BASE_URL = "http://localhost:11434";
    public static final String DEFAULT_MODEL = "llama3.2:3b";

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    private final URI generateUri;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Duration timeout;

    public OllamaLanguageModelClient(String baseUrl, String model) {
        this(baseUrl, model, HttpClient.newHttpClient(), new ObjectMapper(), DEFAULT_TIMEOUT);
    }

    OllamaLanguageModelClient(
            String baseUrl,
            String model,
            HttpClient httpClient,
            ObjectMapper objectMapper,
            Duration timeout
    ) {
        this.generateUri = apiUri(baseUrl);
        this.model = requireText(model, "model");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    public static OllamaLanguageModelClient defaultClient() {
        return new OllamaLanguageModelClient(baseUrlFromEnvironment(), DEFAULT_MODEL);
    }

    public static String baseUrlFromEnvironment() {
        String value = System.getenv("OLLAMA_BASE_URL");
        if (value == null || value.isBlank()) {
            return DEFAULT_BASE_URL;
        }
        return value.trim();
    }

    @Override
    public String generate(String prompt) {
        try {
            String body = objectMapper.writeValueAsString(new OllamaGenerateRequest(
                    model,
                    prompt,
                    false,
                    Map.of("temperature", 0.2)
            ));

            HttpRequest request = HttpRequest.newBuilder(generateUri)
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new LanguageModelException(
                        "Ollama returned HTTP " + response.statusCode() + ": " + truncate(response.body())
                );
            }

            OllamaGenerateResponse ollamaResponse = objectMapper.readValue(response.body(), OllamaGenerateResponse.class);
            if (ollamaResponse.response() == null || ollamaResponse.response().isBlank()) {
                throw new LanguageModelException("Ollama returned an empty response");
            }
            return ollamaResponse.response().trim();
        } catch (IOException exception) {
            throw new LanguageModelException("Could not call Ollama at " + generateUri, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new LanguageModelException("Interrupted while waiting for Ollama", exception);
        }
    }

    private static URI apiUri(String baseUrl) {
        String normalized = requireText(baseUrl, "baseUrl");
        if (!normalized.endsWith("/")) {
            normalized += "/";
        }
        return URI.create(normalized).resolve("api/generate");
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return trimmed;
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        if (value.length() <= 400) {
            return value;
        }
        return value.substring(0, 400) + "...";
    }

    private record OllamaGenerateRequest(
            String model,
            String prompt,
            boolean stream,
            Map<String, Object> options
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OllamaGenerateResponse(String response) {
    }
}
