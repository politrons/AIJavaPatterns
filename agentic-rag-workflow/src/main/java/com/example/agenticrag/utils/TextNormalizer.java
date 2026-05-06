package com.example.agenticrag.utils;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Small text normalization helper used by the lexical retriever.
 */
public final class TextNormalizer {

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "are", "as", "at", "be", "by", "can", "for",
            "from", "has", "how", "in", "into", "is", "it", "of", "on",
            "or", "that", "the", "this", "to", "what", "when", "where",
            "which", "who", "why", "with", "do", "does", "did", "module",
            "repository", "documented", "explain", "main"
    );

    private TextNormalizer() {
    }

    public static List<String> terms(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .map(String::trim)
                .filter(token -> token.length() > 1)
                .filter(token -> !STOP_WORDS.contains(token))
                .toList();
    }

    public static Map<String, Long> termCounts(String text) {
        return terms(text).stream()
                .collect(Collectors.groupingBy(
                        term -> term,
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
    }
}
