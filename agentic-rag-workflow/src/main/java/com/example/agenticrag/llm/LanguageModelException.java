package com.example.agenticrag.llm;

/**
 * Runtime exception used when the configured LLM provider cannot generate a response.
 */
public final class LanguageModelException extends RuntimeException {

    public LanguageModelException(String message) {
        super(message);
    }

    public LanguageModelException(String message, Throwable cause) {
        super(message, cause);
    }
}
