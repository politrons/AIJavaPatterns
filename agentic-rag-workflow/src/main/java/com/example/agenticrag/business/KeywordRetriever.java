package com.example.agenticrag.business;

import com.example.agenticrag.model.RetrievedChunk;
import com.example.agenticrag.model.TextChunk;
import com.example.agenticrag.utils.TextNormalizer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Simple lexical retriever used as the first retrieval strategy.
 *
 * <p>The retriever is separated from the LLM because retrieval is business
 * logic: the application decides what evidence the model is allowed to see.</p>
 */
public final class KeywordRetriever {

    public List<RetrievedChunk> search(String question, List<TextChunk> chunks, int limit) {
        Objects.requireNonNull(chunks, "chunks");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be greater than zero");
        }

        List<String> questionTerms = TextNormalizer.terms(question);
        Set<String> queryTerms = new LinkedHashSet<>(questionTerms);
        if (queryTerms.isEmpty()) {
            return List.of();
        }

        List<RetrievedChunk> results = new ArrayList<>();
        for (TextChunk chunk : chunks) {
            Map<String, Long> chunkTerms = TextNormalizer.termCounts(chunk.text());
            List<String> matchedTerms = queryTerms.stream()
                    .filter(chunkTerms::containsKey)
                    .toList();

            if (!matchedTerms.isEmpty()) {
                double score = score(queryTerms, questionTerms, chunkTerms, chunk.text(), matchedTerms);
                results.add(new RetrievedChunk(chunk, score, matchedTerms));
            }
        }

        return results.stream()
                .sorted(Comparator.comparingDouble(RetrievedChunk::score).reversed()
                        .thenComparing(result -> result.chunk().documentId())
                        .thenComparing(result -> result.chunk().sourcePath().toString())
                        .thenComparingInt(result -> result.chunk().chunkIndex()))
                .limit(limit)
                .toList();
    }

    private double score(
            Set<String> queryTerms,
            List<String> questionTerms,
            Map<String, Long> chunkTerms,
            String chunkText,
            List<String> matchedTerms
    ) {
        long termFrequency = matchedTerms.stream()
                .mapToLong(term -> chunkTerms.getOrDefault(term, 0L))
                .sum();
        double queryCoverage = (double) matchedTerms.size() / (double) queryTerms.size();

        return (matchedTerms.size() * 2.0) + termFrequency + queryCoverage + phraseBoost(questionTerms, chunkText);
    }

    private double phraseBoost(List<String> questionTerms, String chunkText) {
        String normalizedChunk = chunkText.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ");
        double boost = 0;
        for (int index = 0; index < questionTerms.size() - 1; index++) {
            String phrase = questionTerms.get(index) + " " + questionTerms.get(index + 1);
            if (normalizedChunk.contains(phrase)) {
                boost += 4.0;
            }
        }
        return boost;
    }
}
