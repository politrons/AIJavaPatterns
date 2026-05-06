package com.example.agenticrag.model;

import java.util.List;
import java.util.Objects;

/**
 * A chunk returned by the retriever together with the score explanation.
 *
 * <p>The matched terms are included because observability is a core
 * AI-native concern. When the system answers poorly, developers need to see
 * why a piece of evidence was selected.</p>
 */
public record RetrievedChunk(TextChunk chunk, double score, List<String> matchedTerms) {

    public RetrievedChunk {
        chunk = Objects.requireNonNull(chunk, "chunk");
        if (score <= 0) {
            throw new IllegalArgumentException("score must be greater than zero");
        }
        matchedTerms = List.copyOf(Objects.requireNonNull(matchedTerms, "matchedTerms"));
        if (matchedTerms.isEmpty()) {
            throw new IllegalArgumentException("matchedTerms must not be empty");
        }
    }
}
