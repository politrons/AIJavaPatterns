package com.example.agenticrag.model;

import java.util.List;
import java.util.Objects;

/**
 * Final answer returned by the workflow.
 *
 * <p>The answer is separated from citations and evidence previews so callers
 * can render them differently in a UI, log them, or evaluate them in tests.</p>
 */
public record GroundedAnswer(
        String question,
        String answer,
        List<Citation> citations,
        List<String> evidencePreviews
) {

    public GroundedAnswer {
        question = requireText(question, "question");
        answer = requireText(answer, "answer");
        citations = List.copyOf(Objects.requireNonNull(citations, "citations"));
        evidencePreviews = List.copyOf(Objects.requireNonNull(evidencePreviews, "evidencePreviews"));
    }

    public String toMarkdown() {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# Grounded Answer\n\n");
        markdown.append("**Question:** ").append(question).append("\n\n");
        markdown.append(answer).append("\n");

        if (!citations.isEmpty()) {
            markdown.append("\n## Citations\n\n");
            for (Citation citation : citations) {
                markdown.append("- ")
                        .append(citation.label())
                        .append(" (")
                        .append(citation.sourcePath())
                        .append(")\n");
            }
        }

        if (!evidencePreviews.isEmpty()) {
            markdown.append("\n## Evidence Preview\n\n");
            for (String preview : evidencePreviews) {
                markdown.append("- ").append(preview).append("\n");
            }
        }

        return markdown.toString();
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return trimmed;
    }
}
