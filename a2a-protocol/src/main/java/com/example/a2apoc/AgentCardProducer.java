package com.example.a2apoc;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.a2aproject.sdk.server.PublicAgentCard;
import org.a2aproject.sdk.spec.AgentCard;

@ApplicationScoped
public class AgentCardProducer {
    private final AgentSettings settings;
    private final AgentCardFactory cardFactory;

    /**
     * Receives the configuration reader and Agent Card factory from Quarkus CDI.
     */
    @Inject
    public AgentCardProducer(AgentSettings settings, AgentCardFactory cardFactory) {
        this.settings = settings;
        this.cardFactory = cardFactory;
    }

    /**
     * Produces the public A2A Agent Card bean consumed by the A2A Java SDK server
     * integration.
     */
    @Produces
    @PublicAgentCard
    public AgentCard agentCard() {
        return cardFactory.create(settings.toRuntimeConfig());
    }
}
