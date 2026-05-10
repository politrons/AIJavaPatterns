package com.example.kafkallmagent.llm;

/**
 * Runtime exception used when the LLM provider cannot return a response.
 */
public final class LanguageModelException extends RuntimeException {

    /**
     * Creates an exception with a provider-specific failure message.
     *
     * @param message explanation of the language-model failure
     */
    public LanguageModelException(String message) {
        super(message);
    }

    /**
     * Creates an exception with both a readable failure message and the original cause.
     *
     * @param message explanation of the language-model failure
     * @param cause underlying exception thrown by the HTTP client, parser, or runtime
     */
    public LanguageModelException(String message, Throwable cause) {
        super(message, cause);
    }
}
