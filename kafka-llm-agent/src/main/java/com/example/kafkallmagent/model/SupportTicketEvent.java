package com.example.kafkallmagent.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Raw support ticket event consumed from Kafka.
 */
public record SupportTicketEvent(
        String ticketId,
        String customerId,
        String channel,
        String subject,
        String body,
        Instant createdAt
) {

    /**
     * Validates the raw support-ticket payload as soon as it enters the domain model.
     * This keeps downstream enrichment code focused on AI behavior instead of repeatedly checking
     * for missing identifiers, empty text fields, or a null creation timestamp.
     */
    public SupportTicketEvent {
        ticketId = requireText(ticketId, "ticketId");
        customerId = requireText(customerId, "customerId");
        channel = requireText(channel, "channel");
        subject = requireText(subject, "subject");
        body = requireText(body, "body");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
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
