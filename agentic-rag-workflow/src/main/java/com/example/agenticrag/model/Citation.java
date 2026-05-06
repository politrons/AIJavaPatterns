package com.example.agenticrag.model;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Traceable reference to the evidence used in an answer.
 */
public record Citation(String documentId, String title, Path sourcePath, int chunkIndex) {

    public Citation {
        documentId = requireText(documentId, "documentId");
        title = requireText(title, "title");
        sourcePath = Objects.requireNonNull(sourcePath, "sourcePath");
        if (chunkIndex < 0) {
            throw new IllegalArgumentException("chunkIndex must be greater than or equal to zero");
        }
    }

    public String label() {
        return title + " chunk " + chunkIndex;
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
