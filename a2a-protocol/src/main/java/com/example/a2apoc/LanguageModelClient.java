package com.example.a2apoc;

import com.example.a2apoc.A2ARecords.OllamaConfig;

public interface LanguageModelClient {
    /**
     * Sends one chat request to the configured local language model and returns
     * the assistant text.
     */
    String chat(OllamaConfig config, String systemPrompt, String userPrompt);
}
