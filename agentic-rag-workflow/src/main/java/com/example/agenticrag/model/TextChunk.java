package com.example.agenticrag.model;

import java.nio.file.Path;
import java.util.Objects;

/**
 * A small piece of a source document that can be retrieved as evidence.
 *
 * <p>Chunks are the unit of grounding. The final answer should cite chunks,
 * not entire documents, because smaller citations make it easier to verify the
 * model output.</p>
 */
public record TextChunk(
        String documentId,
        String title,
        Path sourcePath,
        int chunkIndex,
        int startOffset,
        int endOffset,
        String text
) {

    public TextChunk {
        documentId = requireText(documentId, "documentId");
        title = requireText(title, "title");
        sourcePath = Objects.requireNonNull(sourcePath, "sourcePath");
        text = requireText(text, "text");
        if (chunkIndex < 0) {
            throw new IllegalArgumentException("chunkIndex must be greater than or equal to zero");
        }
        if (startOffset < 0) {
            throw new IllegalArgumentException("startOffset must be greater than or equal to zero");
        }
        if (endOffset < startOffset) {
            throw new IllegalArgumentException("endOffset must be greater than or equal to startOffset");
        }
    }

    public Citation citation() {
        return new Citation(documentId, title, sourcePath, chunkIndex);
    }

    public String preview(int maxCharacters) {
        if (maxCharacters <= 0) {
            throw new IllegalArgumentException("maxCharacters must be greater than zero");
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxCharacters) {
            return normalized;
        }
        return normalized.substring(0, maxCharacters).trim() + "...";
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
