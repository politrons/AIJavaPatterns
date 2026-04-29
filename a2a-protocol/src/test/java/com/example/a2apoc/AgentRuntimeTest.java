package com.example.a2apoc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.a2apoc.A2ARecords.AgentRequest;
import com.example.a2apoc.A2ARecords.AgentResponse;
import com.example.a2apoc.A2ARecords.AgentRuntimeConfig;
import com.example.a2apoc.A2ARecords.DelegationConfig;
import com.example.a2apoc.A2ARecords.OllamaConfig;

import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.Message;
import org.junit.jupiter.api.Test;

class AgentRuntimeTest {
    private final AgentCardFactory cardFactory = new AgentCardFactory();

    @Test
    void filesystemDelegatesGmailRequestToGmailAgent() {
        AgentRuntimeConfig filesystemConfig = filesystemConfig();
        AgentRuntimeConfig gmailConfig = gmailConfig();
        AgentCard gmailCard = cardFactory.create(gmailConfig);
        FakeAgentClient agentClient = new FakeAgentClient(Map.of(
                "http://127.0.0.1:8002", gmailCard),
                AgentResponse.of("Delegated Gmail answer", Map.of("servedBy", "Gmail Agent")));

        AgentRuntime runtime = new AgentRuntime(
                filesystemConfig,
                cardFactory,
                failIfCalledLanguageModel(),
                agentClient);

        AgentResponse response = runtime.handle(AgentRequest.fromText(
                "Send a Gmail reply to leadership with this message and keep a draft"));

        assertEquals("Delegated Gmail answer", response.text());
        assertEquals("Gmail Agent", response.metadata().get("servedBy"));
        assertEquals("Filesystem Agent", response.metadata().get("delegatedBy"));
        assertEquals("Gmail account planning and operations", response.metadata().get("servedBySkill"));

        Map<?, ?> delegation = (Map<?, ?>) agentClient.lastMessage.metadata().get("delegation");
        assertEquals(1, delegation.get("hopCount"));
        assertTrue(((List<?>) delegation.get("visitedAgents")).contains("Filesystem Agent"));
    }

    @Test
    void gmailDelegatesFilesystemRequestToFilesystemAgent() {
        AgentRuntimeConfig gmailConfig = gmailConfig();
        AgentRuntimeConfig filesystemConfig = filesystemConfig();
        AgentCard filesystemCard = cardFactory.create(filesystemConfig);
        FakeAgentClient agentClient = new FakeAgentClient(Map.of(
                "http://127.0.0.1:8001", filesystemCard),
                AgentResponse.of("Delegated filesystem answer", Map.of("servedBy", "Filesystem Agent")));

        AgentRuntime runtime = new AgentRuntime(
                gmailConfig,
                cardFactory,
                failIfCalledLanguageModel(),
                agentClient);

        AgentResponse response = runtime.handle(AgentRequest.fromText(
                "Read the file /tmp/report.txt, list folders, and summarize the project tree"));

        assertEquals("Delegated filesystem answer", response.text());
        assertEquals("Filesystem Agent", response.metadata().get("servedBy"));
        assertEquals("Gmail Agent", response.metadata().get("delegatedBy"));
        assertEquals("Filesystem planning and operations", response.metadata().get("servedBySkill"));
    }

    @Test
    void filesystemAnswersLocallyWhenOwnSkillScoresHighest() {
        AgentRuntimeConfig filesystemConfig = filesystemConfig();
        AgentCard gmailCard = cardFactory.create(gmailConfig());
        FakeAgentClient agentClient = new FakeAgentClient(Map.of(
                "http://127.0.0.1:8002", gmailCard),
                AgentResponse.of("Should not delegate", Map.of()));

        AgentRuntime runtime = new AgentRuntime(
                filesystemConfig,
                cardFactory,
                (config, systemPrompt, userPrompt) -> {
                    assertEquals("llama3.2:3b", config.model());
                    assertTrue(systemPrompt.contains("Filesystem planning and operations"));
                    assertTrue(userPrompt.contains("List files"));
                    return "Local filesystem answer";
                },
                agentClient);

        AgentResponse response = runtime.handle(AgentRequest.fromText(
                "List files under src/main and read the pom file"));

        assertEquals("Local filesystem answer", response.text());
        assertEquals("Filesystem Agent", response.metadata().get("servedBy"));
        assertEquals("llama3.2:3b", response.metadata().get("servedByModel"));
        assertEquals("Filesystem planning and operations", response.metadata().get("servedBySkill"));
    }

    private static LanguageModelClient failIfCalledLanguageModel() {
        return (config, systemPrompt, userPrompt) -> {
            fail("Language model should not be called when delegation is selected.");
            return "";
        };
    }

    private static AgentRuntimeConfig filesystemConfig() {
        return new AgentRuntimeConfig(
                "filesystem",
                "http://127.0.0.1:8001",
                List.of("http://127.0.0.1:8002"),
                new OllamaConfig("http://localhost:11434", "llama3.2:3b", 0.2, 120),
                new DelegationConfig(true, 2));
    }

    private static AgentRuntimeConfig gmailConfig() {
        return new AgentRuntimeConfig(
                "gmail",
                "http://127.0.0.1:8002",
                List.of("http://127.0.0.1:8001"),
                new OllamaConfig("http://localhost:11434", "llama3.1:8b", 0.3, 120),
                new DelegationConfig(true, 2));
    }

    private static final class FakeAgentClient implements AgentClient {
        private final Map<String, AgentCard> cards = new LinkedHashMap<>();
        private final AgentResponse response;
        private Message lastMessage;

        private FakeAgentClient(Map<String, AgentCard> cards, AgentResponse response) {
            this.cards.putAll(cards);
            this.response = response;
        }

        @Override
        public AgentCard getAgentCard(String baseUrl) {
            AgentCard card = cards.get(baseUrl);
            if (card == null) {
                throw new IllegalStateException("Missing fake card for " + baseUrl);
            }
            return card;
        }

        @Override
        public AgentResponse sendMessage(AgentCard agentCard, Message message) {
            this.lastMessage = message;
            return response;
        }
    }
}
