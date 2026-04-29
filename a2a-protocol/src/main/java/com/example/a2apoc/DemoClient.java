package com.example.a2apoc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.example.a2apoc.A2ARecords.AgentResponse;
import com.example.a2apoc.A2ARecords.CliArgs;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.TextPart;

public final class DemoClient {
    private DemoClient() {
    }

    /**
     * Runs a CLI demo request against the configured agents and prints the final
     * response plus routing metadata.
     */
    public static void main(String[] args) throws Exception {
        CliArgs cliArgs = CliArgs.parse(args);
        if (cliArgs.prompt().isBlank()) {
            System.err.println("Usage: mvn exec:java -Dexec.mainClass=com.example.a2apoc.DemoClient "
                    + "-Dexec.args=\"--prompt 'your request'\"");
            System.exit(2);
        }

        AgentClient client = new A2AAgentClient();
        Map<String, AgentCard> cards = discoverCards(client, cliArgs.agentUrls());
        if (cards.isEmpty()) {
            throw new IllegalStateException("Could not discover any Agent Card. Start the agents first.");
        }

        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        if (cliArgs.showCards()) {
            System.out.println(mapper.writeValueAsString(cards));
        }

        String entryUrl = cliArgs.entryAgent().isBlank()
                ? cards.keySet().iterator().next()
                : cliArgs.entryAgent();
        AgentCard entryCard = Optional.ofNullable(cards.get(entryUrl))
                .orElseGet(() -> client.getAgentCard(entryUrl));

        AgentResponse response = client.sendMessage(entryCard, userMessage(cliArgs.prompt()));

        System.out.println("Entry agent: " + entryUrl);
        System.out.println("Response:");
        System.out.println(response.text());
        System.out.println("Execution metadata:");
        System.out.println(mapper.writeValueAsString(response.metadata()));
    }

    /**
     * Reads Agent Cards from all configured agent URLs, skipping unavailable
     * peers so the demo can still run with any reachable agent.
     */
    private static Map<String, AgentCard> discoverCards(AgentClient client, List<String> agentUrls) {
        Map<String, AgentCard> cards = new LinkedHashMap<>();
        for (String agentUrl : agentUrls) {
            try {
                cards.put(agentUrl, client.getAgentCard(agentUrl));
            } catch (RuntimeException e) {
                System.err.println("[WARN] Could not read Agent Card from " + agentUrl + ": " + e.getMessage());
            }
        }
        return cards;
    }

    /**
     * Builds the initial user A2A message with delegation metadata set to a fresh
     * chain.
     */
    private static Message userMessage(String prompt) {
        return Message.builder()
                .role(Message.Role.ROLE_USER)
                .parts(List.of(new TextPart(prompt)))
                .metadata(Map.of(
                        "source", "DemoClient",
                        "delegation", Map.of("hopCount", 0, "visitedAgents", List.of())))
                .build();
    }

}
