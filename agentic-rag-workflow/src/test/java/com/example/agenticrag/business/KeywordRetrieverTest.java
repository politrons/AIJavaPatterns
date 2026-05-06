package com.example.agenticrag.business;

import com.example.agenticrag.model.RetrievedChunk;
import com.example.agenticrag.model.TextChunk;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeywordRetrieverTest {

    @Test
    void ranksChunksByMatchedQuestionTerms() {
        List<TextChunk> chunks = List.of(
                new TextChunk(
                        "rag",
                        "rag.md",
                        Path.of("rag.md"),
                        0,
                        0,
                        80,
                        "RAG retrieves evidence and returns citations for grounded answers."
                ),
                new TextChunk(
                        "a2a",
                        "a2a.md",
                        Path.of("a2a.md"),
                        0,
                        0,
                        80,
                        "A2A allows agents to delegate tasks to other agents."
                )
        );

        List<RetrievedChunk> results = new KeywordRetriever()
                .search("How does RAG use citations?", chunks, 1);

        assertEquals(1, results.size());
        assertEquals("rag", results.get(0).chunk().documentId());
        assertTrue(results.get(0).matchedTerms().contains("rag"));
        assertTrue(results.get(0).matchedTerms().contains("citations"));
    }
}
