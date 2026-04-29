package com.example.a2apoc;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import com.example.a2apoc.A2ARecords.AgentProfile;
import com.example.a2apoc.A2ARecords.AgentRuntimeConfig;

import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentProvider;
import org.a2aproject.sdk.spec.TransportProtocol;

@ApplicationScoped
public class AgentCardFactory {
    /**
     * Builds the public Agent Card for the active profile.
     *
     * <p>The A2A SDK publishes this card at {@code /.well-known/agent-card.json}
     * through the {@link AgentCardProducer}. Peer agents read this metadata to
     * discover supported transport URLs and skill descriptions.
     */
    public AgentCard create(AgentRuntimeConfig config) {
        AgentProfile profile = AgentProfiles.forKind(config.kind());
        return AgentCard.builder()
                .name(profile.name())
                .description(profile.description())
                .provider(new AgentProvider("AIJavaPatterns Lab", "https://a2a-protocol.org"))
                .version("1.0.0")
                .documentationUrl("https://a2a-protocol.org/latest/")
                .supportedInterfaces(List.of(new AgentInterface(
                        TransportProtocol.HTTP_JSON.asString(),
                        config.baseUrl())))
                .capabilities(AgentCapabilities.builder()
                        .streaming(false)
                        .pushNotifications(false)
                        .build())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(profile.skills())
                .build();
    }
}
