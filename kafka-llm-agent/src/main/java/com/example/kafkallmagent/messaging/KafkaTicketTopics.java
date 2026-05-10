package com.example.kafkallmagent.messaging;

import java.util.Objects;

/**
 * Topic and consumer group names used by the Kafka ticket agent.
 */
public record KafkaTicketTopics(String inputTopic, String outputTopic, String groupId) {

    /**
     * Validates and normalizes the topic names and consumer group used by the pipeline.
     * Each value must be non-null and non-blank because these strings are passed directly to the
     * Kafka clients during topic bootstrap, subscription, and publication.
     */
    public KafkaTicketTopics {
        inputTopic = requireText(inputTopic, "inputTopic");
        outputTopic = requireText(outputTopic, "outputTopic");
        groupId = requireText(groupId, "groupId");
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return trimmed;
    }
}
