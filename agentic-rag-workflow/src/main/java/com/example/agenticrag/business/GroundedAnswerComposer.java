package com.example.agenticrag.business;

import com.example.agenticrag.llm.LanguageModelClient;
import com.example.agenticrag.model.Citation;
import com.example.agenticrag.model.GroundedAnswer;
import com.example.agenticrag.model.RagRequest;
import com.example.agenticrag.model.RetrievedChunk;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Composes a grounded prompt and asks the LLM to answer from retrieved evidence.
 *
 * <p>The important rule is that the model receives only the question and the
 * selected chunks. The prompt also tells the model how to behave when the
 * evidence is insufficient.</p>
 */
public final class GroundedAnswerComposer {

    private static final int EVIDENCE_PREVIEW_CHARACTERS = 260;

    private final LanguageModelClient languageModelClient;

    public GroundedAnswerComposer(LanguageModelClient languageModelClient) {
        this.languageModelClient = Objects.requireNonNull(languageModelClient, "languageModelClient");
    }

    public GroundedAnswer compose(RagRequest request, List<RetrievedChunk> retrievedChunks) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(retrievedChunks, "retrievedChunks");

        if (retrievedChunks.isEmpty()) {
            return new GroundedAnswer(
                    request.question(),
                    "I do not have enough retrieved evidence to answer this question safely.",
                    List.of(),
                    List.of()
            );
        }

        String answer = languageModelClient.generate(buildGroundedPrompt(request, retrievedChunks));

        Set<Citation> citations = new LinkedHashSet<>();
        List<String> evidencePreviews = retrievedChunks.stream()
                .peek(result -> citations.add(result.chunk().citation()))
                .map(this::formatEvidencePreview)
                .toList();

        return new GroundedAnswer(
                request.question(),
                answer,
                List.copyOf(citations),
                evidencePreviews
        );
    }

    private String buildGroundedPrompt(RagRequest request, List<RetrievedChunk> retrievedChunks) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a RAG assistant for a Java learning repository.\n");
        prompt.append("Answer the user question using only the evidence chunks below.\n");
        prompt.append("If the evidence does not support the answer, say that the repository evidence is insufficient.\n");
        prompt.append("Mention citation labels inline when you use evidence.\n");
        prompt.append("Only use citation labels exactly as they appear before each evidence chunk. Do not invent citation labels.\n");
        prompt.append("Keep the answer concise and educational.\n\n");
        prompt.append("Question:\n").append(request.question()).append("\n\n");
        prompt.append("Evidence:\n");

        for (RetrievedChunk result : retrievedChunks) {
            prompt.append("[").append(result.chunk().citation().label()).append("]\n");
            prompt.append(result.chunk().text()).append("\n\n");
        }

        prompt.append("Answer:");
        return prompt.toString();
    }

    private String formatEvidencePreview(RetrievedChunk result) {
        return result.chunk().preview(EVIDENCE_PREVIEW_CHARACTERS)
                + " [matched terms: "
                + String.join(", ", result.matchedTerms())
                + "; source: "
                + result.chunk().citation().label()
                + "]";
    }
}
