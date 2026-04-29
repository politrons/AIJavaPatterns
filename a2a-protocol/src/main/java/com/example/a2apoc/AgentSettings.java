package com.example.a2apoc;

import java.util.Arrays;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import com.example.a2apoc.A2ARecords.AgentRuntimeConfig;
import com.example.a2apoc.A2ARecords.DelegationConfig;
import com.example.a2apoc.A2ARecords.OllamaConfig;

import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class AgentSettings {
    @ConfigProperty(name = "agent.kind")
    String kind;

    @ConfigProperty(name = "agent.base-url")
    String baseUrl;

    @ConfigProperty(name = "agent.peer-urls", defaultValue = "")
    String peerUrls;

    @ConfigProperty(name = "agent.ollama.base-url", defaultValue = "http://localhost:11434")
    String ollamaBaseUrl;

    @ConfigProperty(name = "agent.ollama.model")
    String ollamaModel;

    @ConfigProperty(name = "agent.ollama.temperature", defaultValue = "0.2")
    double ollamaTemperature;

    @ConfigProperty(name = "agent.ollama.timeout-seconds", defaultValue = "120")
    int ollamaTimeoutSeconds;

    @ConfigProperty(name = "agent.delegation.enabled", defaultValue = "true")
    boolean delegationEnabled;

    @ConfigProperty(name = "agent.delegation.max-hops", defaultValue = "2")
    int maxHops;

    /**
     * Converts Quarkus/MicroProfile configuration properties into the immutable
     * runtime config consumed by the agent runtime and Agent Card factory.
     */
    public AgentRuntimeConfig toRuntimeConfig() {
        return new AgentRuntimeConfig(
                kind,
                baseUrl,
                parsePeerUrls(peerUrls),
                new OllamaConfig(ollamaBaseUrl, ollamaModel, ollamaTemperature, ollamaTimeoutSeconds),
                new DelegationConfig(delegationEnabled, maxHops));
    }

    /**
     * Splits the comma-separated peer URL property into normalized base URLs.
     */
    private static List<String> parsePeerUrls(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }
}
