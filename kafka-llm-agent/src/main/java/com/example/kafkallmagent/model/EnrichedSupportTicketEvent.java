package com.example.kafkallmagent.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Output event published after LLM enrichment.
 */
public record EnrichedSupportTicketEvent(
        SupportTicketEvent originalTicket,
        TicketEnrichment enrichment,
        Instant processedAt
) {

    /**
     * Creates the final event published to the enriched Kafka topic.
     * All parts are required because the output event is meant to be self-contained: it preserves
     * the original ticket, the LLM enrichment, and the time at which enrichment completed.
     */
    public EnrichedSupportTicketEvent {
        originalTicket = Objects.requireNonNull(originalTicket, "originalTicket");
        enrichment = Objects.requireNonNull(enrichment, "enrichment");
        processedAt = Objects.requireNonNull(processedAt, "processedAt");
    }
}
