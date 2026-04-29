package com.example.a2apoc;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import com.example.a2apoc.A2ARecords.OllamaConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@ApplicationScoped
public class OllamaLanguageModelClient implements LanguageModelClient {
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Calls Ollama's local {@code /api/chat} endpoint with a system prompt, user
     * prompt, model name, timeout, and temperature from runtime configuration.
     */
    @Override
    public String chat(OllamaConfig config, String systemPrompt, String userPrompt) {
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "model", config.model(),
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)),
                    "stream", false,
                    "options", Map.of("temperature", config.temperature())));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.baseUrl().replaceAll("/+$", "") + "/api/chat"))
                    .timeout(Duration.ofSeconds(config.timeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Ollama returned HTTP " + response.statusCode() + ": " + response.body());
            }

            JsonNode root = objectMapper.readTree(response.body());
            String text = root.path("message").path("content").asText("").trim();
            if (text.isEmpty()) {
                throw new IllegalStateException("Ollama returned an empty message.content");
            }
            return text;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to call Ollama model " + config.model() + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calling Ollama model " + config.model(), e);
        }
    }
}
