package com.example.agenticrag.business;

import com.example.agenticrag.model.RagRequest;
import com.example.agenticrag.model.RetrievedChunk;
import com.example.agenticrag.model.SourceDocument;
import com.example.agenticrag.model.TextChunk;

import java.util.List;
import java.util.Objects;

/**
 * Stores chunks and exposes retrieval for the workflow.
 *
 * <p>The knowledge base owns indexed evidence. The rest of the workflow asks
 * for relevant chunks instead of reading documents directly.</p>
 */
public final class InMemoryKnowledgeBase {

    private final List<TextChunk> chunks;
    private final KeywordRetriever retriever;

    public InMemoryKnowledgeBase(List<TextChunk> chunks, KeywordRetriever retriever) {
        this.chunks = List.copyOf(Objects.requireNonNull(chunks, "chunks"));
        this.retriever = Objects.requireNonNull(retriever, "retriever");
    }

    public static InMemoryKnowledgeBase fromDocuments(List<SourceDocument> documents, TextChunker chunker) {
        Objects.requireNonNull(documents, "documents");
        Objects.requireNonNull(chunker, "chunker");

        List<TextChunk> chunks = documents.stream()
                .flatMap(document -> chunker.split(document).stream())
                .toList();

        return new InMemoryKnowledgeBase(chunks, new KeywordRetriever());
    }

    public List<RetrievedChunk> retrieve(RagRequest request) {
        Objects.requireNonNull(request, "request");
        return retriever.search(request.question(), chunks, request.maxEvidence());
    }

    public List<TextChunk> chunks() {
        return chunks;
    }
}
