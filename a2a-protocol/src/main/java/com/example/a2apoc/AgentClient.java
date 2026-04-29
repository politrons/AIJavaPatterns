package com.example.a2apoc;

import com.example.a2apoc.A2ARecords.AgentResponse;

import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.Message;

public interface AgentClient {
    /**
     * Reads an Agent Card from a peer agent base URL.
     */
    AgentCard getAgentCard(String baseUrl);

    /**
     * Sends a user message to the peer represented by the supplied Agent Card.
     */
    AgentResponse sendMessage(AgentCard agentCard, Message message);
}
