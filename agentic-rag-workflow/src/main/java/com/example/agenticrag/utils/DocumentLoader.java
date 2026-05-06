package com.example.agenticrag.utils;

import com.example.agenticrag.model.SourceDocument;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Loads local files as source documents.
 *
 * <p>This utility keeps ingestion explicit: trusted files in, UTF-8 text out.
 * Business code receives source documents with metadata already attached.</p>
 */
public final class DocumentLoader {

    public SourceDocument load(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        Path normalizedPath = path.toAbsolutePath().normalize();
        String content = Files.readString(normalizedPath, StandardCharsets.UTF_8);

        return new SourceDocument(
                documentId(normalizedPath),
                normalizedPath.getFileName().toString(),
                normalizedPath,
                content
        );
    }

    public List<SourceDocument> loadAll(List<Path> paths) throws IOException {
        Objects.requireNonNull(paths, "paths");
        List<SourceDocument> documents = new java.util.ArrayList<>();
        for (Path path : paths) {
            documents.add(load(path));
        }
        return List.copyOf(documents);
    }

    private String documentId(Path path) {
        String raw = path.getFileName().toString().toLowerCase();
        return raw.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }
}
