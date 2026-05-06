package com.example.agenticrag.llm;

/**
 * Boundary between business workflow code and a concrete language model.
 *
 * <p>The RAG workflow depends on this interface, not directly on Ollama. That
 * keeps tests deterministic and makes provider changes localized.</p>
 */
@FunctionalInterface
public interface LanguageModelClient {

    String generate(String prompt);
}
