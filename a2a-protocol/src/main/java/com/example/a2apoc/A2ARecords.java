package com.example.a2apoc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentSkill;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.TextPart;

/**
 * Groups the small immutable data carriers used by the PoC so the domain model
 * stays easy to scan in one place.
 */
public final class A2ARecords {
    private A2ARecords() {
    }

    public static record OllamaConfig(
            String baseUrl,
            String model,
            double temperature,
            int timeoutSeconds) {
    }

    public static record DelegationConfig(boolean enabled, int maxHops) {
    }

    public static record AgentRuntimeConfig(
            String kind,
            String baseUrl,
            List<String> peerUrls,
            OllamaConfig ollama,
            DelegationConfig delegation) {
    }

    public static record AgentProfile(
            String kind,
            String name,
            String description,
            String systemPrompt,
            List<AgentSkill> skills) {
    }

    public static record ScoredSkill(Optional<AgentSkill> skill, double score) {
        public ScoredSkill {
            skill = skill == null ? Optional.empty() : skill;
        }

        /**
         * Creates a scored skill from SDK data that may not contain a matching
         * skill.
         */
        public static ScoredSkill ofNullable(AgentSkill skill, double score) {
            return new ScoredSkill(Optional.ofNullable(skill), score);
        }

        /**
         * Returns the user-facing skill name, falling back to the skill id when the
         * Agent Card does not define a name.
         */
        public Optional<String> skillName() {
            return skill.map(value -> value.name() != null ? value.name() : value.id());
        }
    }

    public static record AgentRequest(String text, Message message) {
        /**
         * Builds a minimal text-only A2A request for tests and local runtime calls.
         */
        public static AgentRequest fromText(String text) {
            Message message = Message.builder()
                    .role(Message.Role.ROLE_USER)
                    .parts(List.of(new TextPart(text)))
                    .metadata(Map.of(
                            "source", "unit-test",
                            "delegation", Map.of("hopCount", 0, "visitedAgents", List.of())))
                    .build();
            return new AgentRequest(text, message);
        }
    }

    public static record AgentResponse(String text, Map<String, Object> metadata) {
        /**
         * Creates a response and normalizes missing metadata to an empty map.
         */
        public static AgentResponse of(String text, Map<String, Object> metadata) {
            return new AgentResponse(text, metadata == null ? Map.of() : metadata);
        }
    }

    public static record DelegationState(int hopCount, Set<String> visitedAgents) {
        /**
         * Rehydrates delegation state from A2A message metadata.
         *
         * <p>Missing or malformed metadata is treated as a fresh request so one bad
         * peer response cannot break local handling.
         */
        public static DelegationState fromMetadata(Map<String, Object> metadata) {
            if (metadata == null) {
                return new DelegationState(0, new LinkedHashSet<>());
            }
            Object rawDelegation = metadata.get("delegation");
            if (!(rawDelegation instanceof Map<?, ?> delegation)) {
                return new DelegationState(0, new LinkedHashSet<>());
            }

            int hopCount = intValue(delegation.get("hopCount"));
            Set<String> visitedAgents = new LinkedHashSet<>();
            Object rawVisitedAgents = delegation.get("visitedAgents");
            if (rawVisitedAgents instanceof Collection<?> values) {
                for (Object value : values) {
                    if (value != null) {
                        visitedAgents.add(String.valueOf(value));
                    }
                }
            }
            return new DelegationState(hopCount, visitedAgents);
        }

        /**
         * Returns a new state that marks the supplied agent as already visited.
         */
        public DelegationState withVisited(String agentName) {
            Set<String> visited = new LinkedHashSet<>(visitedAgents);
            visited.add(agentName);
            return new DelegationState(hopCount, visited);
        }

        /**
         * Builds the metadata payload that will be attached to the next delegated
         * A2A message.
         */
        public Map<String, Object> metadataForNextHop(String currentAgentName) {
            Set<String> nextVisited = new LinkedHashSet<>(visitedAgents);
            nextVisited.add(currentAgentName);

            Map<String, Object> delegation = new LinkedHashMap<>();
            delegation.put("hopCount", hopCount + 1);
            delegation.put("visitedAgents", new ArrayList<>(nextVisited));
            return delegation;
        }

        /**
         * Returns the visited-agent set as an ordered list for metadata serialization.
         */
        public List<String> visitedAgentsList() {
            return new ArrayList<>(visitedAgents);
        }

        /**
         * Converts untyped metadata values into a safe hop count.
         */
        private static int intValue(Object value) {
            if (value instanceof Number number) {
                return number.intValue();
            }
            if (value instanceof String text) {
                try {
                    return Integer.parseInt(text);
                } catch (NumberFormatException ignored) {
                    return 0;
                }
            }
            return 0;
        }
    }

    public static record PeerPick(String url, AgentCard card, ScoredSkill skill) {
    }

    public static record CliArgs(String prompt, List<String> agentUrls, String entryAgent, boolean showCards) {
        private static final String DEFAULT_AGENTS = "http://127.0.0.1:8001,http://127.0.0.1:8002";

        /**
         * Parses the demo CLI flags into a structured configuration object.
         */
        public static CliArgs parse(String[] args) {
            Map<String, String> values = new LinkedHashMap<>();
            boolean showCards = false;
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if ("--show-cards".equals(arg)) {
                    showCards = true;
                } else if (arg.startsWith("--") && i + 1 < args.length) {
                    values.put(arg, args[++i]);
                }
            }

            String agents = values.getOrDefault("--agents", DEFAULT_AGENTS);
            List<String> agentUrls = Arrays.stream(agents.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .collect(Collectors.toList());

            String entryAgent = values.getOrDefault("--entry-agent", "");
            if (!entryAgent.isBlank() && !agentUrls.contains(entryAgent)) {
                agentUrls.add(entryAgent);
            }
            return new CliArgs(values.getOrDefault("--prompt", ""), agentUrls, entryAgent, showCards);
        }
    }
}
