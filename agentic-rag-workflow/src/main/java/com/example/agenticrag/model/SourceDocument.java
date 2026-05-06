package com.example.agenticrag.model;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Raw trusted input before any RAG processing happens.
 *
 * <p>A RAG system should keep source metadata together with the text from the
 * beginning. If metadata is lost during ingestion, the final answer cannot
 * provide useful citations.</p>
 */
public record SourceDocument(String id, String title, Path sourcePath, String content) {

    public SourceDocument {
        id = requireText(id, "id");
        title = requireText(title, "title");
        sourcePath = Objects.requireNonNull(sourcePath, "sourcePath");
        content = Objects.requireNonNull(content, "content");
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
