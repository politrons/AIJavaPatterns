package com.example.kafkallmagent.llm;

/**
 * Boundary between business logic and a concrete LLM provider.
 */
@FunctionalInterface
public interface LanguageModelClient {

    /**
     * Sends a prompt to the underlying language model and returns the raw text response exactly as
     * received by the business layer.
     *
     * @param prompt full prompt prepared by the calling workflow
     * @return raw model output before any JSON sanitization or parsing
     */
    String generate(String prompt);
}
