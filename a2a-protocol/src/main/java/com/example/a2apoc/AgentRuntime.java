package com.example.a2apoc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.example.a2apoc.A2ARecords.AgentProfile;
import com.example.a2apoc.A2ARecords.AgentRequest;
import com.example.a2apoc.A2ARecords.AgentResponse;
import com.example.a2apoc.A2ARecords.AgentRuntimeConfig;
import com.example.a2apoc.A2ARecords.DelegationState;
import com.example.a2apoc.A2ARecords.PeerPick;
import com.example.a2apoc.A2ARecords.ScoredSkill;

import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.Message;

@ApplicationScoped
public class AgentRuntime {
    private final AgentRuntimeConfig config;
    private final AgentCard agentCard;
    private final AgentProfile selfProfile;
    private final LanguageModelClient languageModelClient;
    private final AgentClient agentClient;
    private final Map<String, AgentCard> peerCards = new ConcurrentHashMap<>();

    /**
     * CDI constructor used by Quarkus to wire runtime settings, local LLM access,
     * and the A2A peer client.
     */
    @Inject
    public AgentRuntime(
            AgentSettings settings,
            AgentCardFactory cardFactory,
            LanguageModelClient languageModelClient,
            AgentClient agentClient) {
        this(settings.toRuntimeConfig(), cardFactory, languageModelClient, agentClient);
    }

    /**
     * Test-friendly constructor that accepts already materialized runtime config
     * and replaceable collaborators.
     */
    public AgentRuntime(
            AgentRuntimeConfig config,
            AgentCardFactory cardFactory,
            LanguageModelClient languageModelClient,
            AgentClient agentClient) {
        this.config = config;
        this.agentCard = cardFactory.create(config);
        this.selfProfile = AgentProfiles.forKind(config.kind());
        this.languageModelClient = languageModelClient;
        this.agentClient = agentClient;
    }

    /**
     * Returns the local Agent Card advertised by this running profile.
     */
    public AgentCard selfCard() {
        return agentCard;
    }

    /**
     * Handles one inbound A2A request.
     *
     * <p>The runtime first scores the local skills, then compares eligible peer
     * skills. If a peer has a stronger match and the hop limit allows delegation,
     * the request is forwarded. Otherwise the active Ollama model answers locally.
     */
    public AgentResponse handle(AgentRequest request) {
        DelegationState incomingState = DelegationState.fromMetadata(request.message().metadata());
        DelegationState localState = incomingState.withVisited(agentCard.name());
        ScoredSkill localSkill = SkillMatcher.bestSkill(request.text(), agentCard);

        Optional<AgentResponse> delegatedResponse = Optional.empty();
        if (config.delegation().enabled() && incomingState.hopCount() < config.delegation().maxHops()) {
            delegatedResponse = pickPeer(request.text(), localState.visitedAgents(), localSkill.score())
                    .flatMap(peerPick -> tryDelegate(request, incomingState, peerPick)
                            .filter(response -> !response.text().isBlank())
                            .map(response -> delegatedResult(response, localSkill, peerPick, incomingState)));
        }

        return delegatedResponse.orElseGet(() -> answerLocally(request.text(), localSkill));
    }

    /**
     * Selects the best peer Agent Card whose skill score beats the local score and
     * has not already been visited in the current delegation chain.
     */
    private Optional<PeerPick> pickPeer(String text, Set<String> visitedAgents, double localScore) {
        Optional<PeerPick> bestPick = Optional.empty();
        double bestScore = localScore;

        for (String peerUrl : config.peerUrls()) {
            Optional<AgentCard> peerCard = getPeerCard(peerUrl);
            if (peerCard.isEmpty() || visitedAgents.contains(peerCard.get().name())) {
                continue;
            }

            ScoredSkill peerSkill = SkillMatcher.bestSkill(text, peerCard.get());
            if (peerSkill.score() > bestScore) {
                bestScore = peerSkill.score();
                bestPick = Optional.of(new PeerPick(peerUrl, peerCard.get(), peerSkill));
            }
        }
        return bestPick;
    }

    /**
     * Resolves and caches a peer Agent Card by base URL.
     *
     * <p>Discovery failures are converted to {@link Optional#empty()} so routing
     * can continue with any other available peers.
     */
    private Optional<AgentCard> getPeerCard(String peerUrl) {
        try {
            AgentCard cached = peerCards.get(peerUrl);
            if (cached != null) {
                return Optional.of(cached);
            }
            AgentCard card = agentClient.getAgentCard(peerUrl);
            if (card == null) {
                return Optional.empty();
            }
            peerCards.put(peerUrl, card);
            return Optional.of(card);
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    /**
     * Attempts to delegate the request to the selected peer.
     *
     * <p>Delegation errors are intentionally swallowed so the local agent can fall
     * back to answering instead of failing the whole user request.
     */
    private Optional<AgentResponse> tryDelegate(AgentRequest request, DelegationState incomingState, PeerPick peerPick) {
        try {
            Message delegatedMessage = delegatedMessage(request.message(), incomingState);
            return Optional.ofNullable(agentClient.sendMessage(peerPick.card(), delegatedMessage));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    /**
     * Clones the inbound user message into a new A2A message and advances the
     * delegation metadata for the next hop.
     */
    private Message delegatedMessage(Message originalMessage, DelegationState incomingState) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (originalMessage.metadata() != null) {
            metadata.putAll(originalMessage.metadata());
        }
        metadata.put("delegation", incomingState.metadataForNextHop(agentCard.name()));

        Message.Builder builder = Message.builder()
                .role(Message.Role.ROLE_USER)
                .messageId(UUID.randomUUID().toString())
                .parts(originalMessage.parts())
                .metadata(metadata);
        if (originalMessage.contextId() != null) {
            builder.contextId(originalMessage.contextId());
        }
        return builder.build();
    }

    /**
     * Wraps a peer response with routing metadata that explains who served the
     * request and why this agent delegated.
     */
    private AgentResponse delegatedResult(
            AgentResponse delegatedResponse,
            ScoredSkill localSkill,
            PeerPick peerPick,
            DelegationState incomingState) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("servedBy", peerPick.card().name());
        metadata.put("servedBySkill", safeSkillName(peerPick.skill()));
        metadata.put("delegatedBy", agentCard.name());
        metadata.put("routing", Map.of(
                "localScore", localSkill.score(),
                "peerScore", peerPick.skill().score()));

        Set<String> visitedAgents = new LinkedHashSet<>(incomingState.visitedAgents());
        visitedAgents.add(agentCard.name());
        visitedAgents.add(peerPick.card().name());
        metadata.put("delegation", Map.of(
                "hopCount", incomingState.hopCount() + 1,
                "visitedAgents", new ArrayList<>(visitedAgents)));

        if (!delegatedResponse.metadata().isEmpty()) {
            metadata.put("peerMetadata", delegatedResponse.metadata());
        }
        return AgentResponse.of(delegatedResponse.text(), metadata);
    }

    /**
     * Uses the local Ollama model to answer when no peer is a better delegation
     * target or delegation is not available.
     */
    private AgentResponse answerLocally(String userText, ScoredSkill localSkill) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("servedBy", agentCard.name());
        metadata.put("servedByModel", config.ollama().model());
        metadata.put("servedBySkill", safeSkillName(localSkill));
        metadata.put("localScore", localSkill.score());

        try {
            String answer = languageModelClient.chat(config.ollama(), systemPrompt(localSkill), userText);
            return AgentResponse.of(answer, metadata);
        } catch (RuntimeException e) {
            metadata.put("error", e.getMessage());
            return AgentResponse.of("Agent failed while using Ollama: " + e.getMessage(), metadata);
        }
    }

    /**
     * Builds the system prompt sent to Ollama from the active agent profile and
     * the skill selected for this request.
     */
    private String systemPrompt(ScoredSkill localSkill) {
        List<String> lines = new ArrayList<>();
        lines.add(selfProfile.systemPrompt().strip());
        lines.add("");
        lines.add("Skills declared in this Agent Card:");
        agentCard.skills().forEach(skill -> lines.add("- " + skill.name() + " (" + skill.id() + "): " + skill.description()));
        if (localSkill.skill().isPresent()) {
            lines.add("");
            lines.add("Target skill for this request: " + safeSkillName(localSkill));
        }
        lines.add("");
        lines.add("Respond in a practical way. State assumptions when context or connected tools are missing.");
        return String.join("\n", lines);
    }

    /**
     * Normalizes a potentially missing skill name for response metadata.
     */
    private static String safeSkillName(ScoredSkill skill) {
        return skill.skillName().orElse("");
    }

}
