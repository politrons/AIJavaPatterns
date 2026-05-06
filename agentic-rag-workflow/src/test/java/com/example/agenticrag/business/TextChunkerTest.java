package com.example.agenticrag.business;

import com.example.agenticrag.model.SourceDocument;
import com.example.agenticrag.model.TextChunk;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextChunkerTest {

    @Test
    void splitsDocumentIntoTraceableChunks() {
        SourceDocument document = new SourceDocument(
                "guide",
                "guide.md",
                Path.of("guide.md"),
                "RAG starts with trusted source documents. "
                        + "The workflow splits those documents into chunks. "
                        + "Each chunk keeps source metadata for citations."
        );

        TextChunker chunker = new TextChunker(70, 10);
        List<TextChunk> chunks = chunker.split(document);

        assertFalse(chunks.isEmpty());
        assertEquals("guide", chunks.get(0).documentId());
        assertEquals(Path.of("guide.md"), chunks.get(0).sourcePath());
        assertTrue(chunks.stream().allMatch(chunk -> chunk.text().length() <= 70));
    }

    @Test
    void returnsNoChunksForBlankContent() {
        SourceDocument document = new SourceDocument("empty", "empty.md", Path.of("empty.md"), "   ");

        List<TextChunk> chunks = new TextChunker(100, 10).split(document);

        assertTrue(chunks.isEmpty());
    }
}
