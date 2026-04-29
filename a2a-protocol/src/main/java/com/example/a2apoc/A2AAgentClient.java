package com.example.a2apoc;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import jakarta.enterprise.context.ApplicationScoped;

import com.example.a2apoc.A2ARecords.AgentResponse;

import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.client.ClientEvent;
import org.a2aproject.sdk.client.MessageEvent;
import org.a2aproject.sdk.client.TaskEvent;
import org.a2aproject.sdk.client.TaskUpdateEvent;
import org.a2aproject.sdk.client.config.ClientConfig;
import org.a2aproject.sdk.client.http.A2ACardResolver;
import org.a2aproject.sdk.client.transport.rest.RestTransport;
import org.a2aproject.sdk.client.transport.rest.RestTransportConfig;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.Message;

@ApplicationScoped
public class A2AAgentClient implements AgentClient {
    /**
     * Fetches a peer Agent Card through the A2A SDK card resolver.
     */
    @Override
    public AgentCard getAgentCard(String baseUrl) {
        try {
            return new A2ACardResolver(baseUrl).getAgentCard();
        } catch (Exception e) {
            throw new IllegalStateException("Could not read Agent Card from " + baseUrl + ": " + e.getMessage(), e);
        }
    }

    /**
     * Sends a non-streaming A2A message using the REST transport and converts the
     * SDK callback event into the local response record.
     */
    @Override
    public AgentResponse sendMessage(AgentCard agentCard, Message message) {
        AtomicReference<AgentResponse> responseRef = new AtomicReference<>();
        BiConsumer<ClientEvent, AgentCard> consumer = (event, card) -> {
            if (event instanceof MessageEvent messageEvent) {
                responseRef.set(ResponseExtractor.fromMessage(messageEvent.getMessage()));
            } else if (event instanceof TaskEvent taskEvent) {
                responseRef.set(ResponseExtractor.fromTask(taskEvent.getTask()));
            } else if (event instanceof TaskUpdateEvent taskUpdateEvent) {
                responseRef.set(ResponseExtractor.fromTask(taskUpdateEvent.getTask()));
            }
        };

        ClientConfig clientConfig = new ClientConfig.Builder()
                .setStreaming(false)
                .setAcceptedOutputModes(List.of("text"))
                .build();

        try (Client client = Client.builder(agentCard)
                .clientConfig(clientConfig)
                .withTransport(RestTransport.class, new RestTransportConfig())
                .addConsumer(consumer)
                .build()) {
            client.sendMessage(message);
        } catch (Exception e) {
            throw new IllegalStateException("A2A message send failed for " + agentCard.name() + ": " + e.getMessage(), e);
        }

        return Optional.ofNullable(responseRef.get())
                .orElseThrow(() -> new IllegalStateException(
                        "A2A peer " + agentCard.name() + " did not return a message or task response"));
    }
}
