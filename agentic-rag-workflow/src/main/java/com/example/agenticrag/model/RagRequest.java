package com.example.agenticrag.model;

import java.util.Objects;

/**
 * User input plus retrieval controls for a single RAG request.
 */
public record RagRequest(String question, int maxEvidence) {

    public static final int DEFAULT_MAX_EVIDENCE = 3;

    public RagRequest {
        Objects.requireNonNull(question, "question");
        question = question.trim();
        if (question.isEmpty()) {
            throw new IllegalArgumentException("question must not be blank");
        }
        if (maxEvidence <= 0) {
            throw new IllegalArgumentException("maxEvidence must be greater than zero");
        }
    }

    public static RagRequest of(String question) {
        return new RagRequest(question, DEFAULT_MAX_EVIDENCE);
    }
}
