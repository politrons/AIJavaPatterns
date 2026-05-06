package com.example.agenticrag.business;

import com.example.agenticrag.llm.LanguageModelClient;
import com.example.agenticrag.model.GroundedAnswer;
import com.example.agenticrag.model.SourceDocument;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagWorkflowTest {

    private static final LanguageModelClient FAKE_LLM = prompt -> "LLM answer grounded in retrieved evidence.";

    @Test
    void answersFromRetrievedEvidenceAndKeepsCitations() {
        SourceDocument document = new SourceDocument(
                "rag-guide",
                "rag-guide.md",
                Path.of("rag-guide.md"),
                "RAG reduces hallucinations by retrieving evidence before composing an answer. "
                        + "Grounded answers include citations so every important claim can be traced."
        );

        GroundedAnswer answer = RagWorkflow.fromDocuments(List.of(document), FAKE_LLM)
                .answer("How does RAG reduce hallucinations with citations?");

        assertEquals("LLM answer grounded in retrieved evidence.", answer.answer());
        assertEquals(1, answer.citations().size());
        assertEquals("rag-guide", answer.citations().get(0).documentId());
        assertFalse(answer.evidencePreviews().isEmpty());
    }

    @Test
    void refusesToAnswerWhenNoEvidenceIsRetrieved() {
        SourceDocument document = new SourceDocument(
                "rag-guide",
                "rag-guide.md",
                Path.of("rag-guide.md"),
                "RAG retrieves project evidence for grounded answers."
        );

        GroundedAnswer answer = RagWorkflow.fromDocuments(List.of(document), FAKE_LLM)
                .answer("Explain Kubernetes cluster autoscaling.");

        assertTrue(answer.answer().contains("I do not have enough retrieved evidence"));
        assertTrue(answer.citations().isEmpty());
    }
}
