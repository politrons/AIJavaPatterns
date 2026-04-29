package com.example.a2apoc;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import com.example.a2apoc.A2ARecords.ScoredSkill;

import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentSkill;

public final class SkillMatcher {
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{L}0-9]+");

    private SkillMatcher() {
    }

    /**
     * Finds the highest-scoring skill declared by an Agent Card for a user text.
     */
    public static ScoredSkill bestSkill(String text, AgentCard card) {
        Optional<AgentSkill> bestSkill = Optional.empty();
        double bestScore = 0.0;
        for (AgentSkill skill : card.skills()) {
            double score = score(text, skill);
            if (score > bestScore) {
                bestScore = score;
                bestSkill = Optional.of(skill);
            }
        }
        return new ScoredSkill(bestSkill, bestScore);
    }

    /**
     * Computes a lightweight lexical match score from skill tags, examples, and
     * name.
     */
    public static double score(String text, AgentSkill skill) {
        String lowered = normalize(text);
        double score = 0.0;

        for (String tag : safeList(skill.tags())) {
            if (!tag.isBlank() && lowered.contains(normalize(tag))) {
                score += 2.0;
            }
        }

        for (String example : safeList(skill.examples())) {
            for (String token : tokens(example)) {
                if (token.length() > 3 && lowered.contains(token)) {
                    score += 0.4;
                }
            }
        }

        if (skill.name() != null && lowered.contains(normalize(skill.name()))) {
            score += 2.0;
        }
        return score;
    }

    /**
     * Treats missing skill metadata lists as empty lists.
     */
    private static List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }

    /**
     * Tokenizes normalized example text for partial matching against the request.
     */
    private static List<String> tokens(String text) {
        return TOKEN_PATTERN.matcher(normalize(text))
                .results()
                .map(match -> match.group())
                .toList();
    }

    /**
     * Normalizes text for case-insensitive matching.
     */
    private static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }
}
