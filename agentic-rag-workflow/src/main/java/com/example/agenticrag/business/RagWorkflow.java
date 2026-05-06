package com.example.agenticrag.business;

import com.example.agenticrag.llm.LanguageModelClient;
import com.example.agenticrag.llm.OllamaLanguageModelClient;
import com.example.agenticrag.model.GroundedAnswer;
import com.example.agenticrag.model.RagRequest;
import com.example.agenticrag.model.RetrievedChunk;
import com.example.agenticrag.model.SourceDocument;

import java.util.List;
import java.util.Objects;

/**
 * Orchestrates the full RAG flow for one request.
 *
 * <p>The workflow is intentionally explicit: retrieve evidence first, build a
 * grounded prompt, then call the LLM. This keeps model behavior constrained by
 * application-owned evidence.</p>
 */
public final class RagWorkflow {

    public static final int DEFAULT_CHUNK_SIZE = 700;
    public static final int DEFAULT_CHUNK_OVERLAP = 100;

    private final InMemoryKnowledgeBase knowledgeBase;
    private final GroundedAnswerComposer answerComposer;

    public RagWorkflow(InMemoryKnowledgeBase knowledgeBase, GroundedAnswerComposer answerComposer) {
        this.knowledgeBase = Objects.requireNonNull(knowledgeBase, "knowledgeBase");
        this.answerComposer = Objects.requireNonNull(answerComposer, "answerComposer");
    }

    public static RagWorkflow fromDocuments(List<SourceDocument> documents, LanguageModelClient languageModelClient) {
        TextChunker chunker = new TextChunker(DEFAULT_CHUNK_SIZE, DEFAULT_CHUNK_OVERLAP);
        InMemoryKnowledgeBase knowledgeBase = InMemoryKnowledgeBase.fromDocuments(documents, chunker);
        return new RagWorkflow(knowledgeBase, new GroundedAnswerComposer(languageModelClient));
    }

    public GroundedAnswer answer(String question) {
        return answer(RagRequest.of(question));
    }

    public GroundedAnswer answer(RagRequest request) {
        Objects.requireNonNull(request, "request");
        List<RetrievedChunk> retrievedChunks = knowledgeBase.retrieve(request);
        return answerComposer.compose(request, retrievedChunks);
    }
}
