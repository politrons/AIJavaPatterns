package com.example.kafkallmagent.business;

import com.example.kafkallmagent.model.EnrichedSupportTicketEvent;
import com.example.kafkallmagent.model.SupportTicketEvent;
import com.example.kafkallmagent.model.TicketEnrichment;

import java.time.Instant;
import java.util.Objects;

/**
 * Application-level agent that enriches one ticket event at a time.
 */
public final class EventEnrichmentAgent {

    private final TicketEnrichmentService enrichmentService;

    /**
     * Creates the application-level agent that delegates classification and enrichment to the
     * business service. Keeping this class small makes the orchestration boundary explicit.
     *
     * @param enrichmentService service responsible for prompt creation, model invocation, and
     *                          structured enrichment parsing
     */
    public EventEnrichmentAgent(TicketEnrichmentService enrichmentService) {
        this.enrichmentService = Objects.requireNonNull(enrichmentService, "enrichmentService");
    }

    /**
     * Processes one raw support-ticket event and returns the enriched event ready to be published.
     * The method preserves the original payload, attaches the LLM enrichment, and records the time
     * when the agent completed processing.
     *
     * @param ticket raw support-ticket event consumed from Kafka
     * @return enriched event containing the original ticket, the generated enrichment, and the
     *         processing timestamp
     */
    public EnrichedSupportTicketEvent process(SupportTicketEvent ticket) {
        Objects.requireNonNull(ticket, "ticket");
        TicketEnrichment enrichment = enrichmentService.enrich(ticket);
        return new EnrichedSupportTicketEvent(ticket, enrichment, Instant.now());
    }
}
