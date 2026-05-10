package com.example.kafkallmagent.business;

import com.example.kafkallmagent.model.SupportTicketEvent;

import java.util.Objects;

/**
 * Builds a strict prompt that asks the model for structured ticket enrichment.
 */
public final class TicketPromptFactory {

    /**
     * Builds the prompt sent to the language model for one support ticket. The prompt constrains
     * the response shape so downstream Kafka processing can deserialize the result deterministically
     * instead of relying on free-form text.
     *
     * @param ticket raw support-ticket event whose fields will be embedded into the prompt
     * @return prompt instructing the model to return JSON only with the expected enrichment fields
     */
    public String buildPrompt(SupportTicketEvent ticket) {
        Objects.requireNonNull(ticket, "ticket");

        return """
                You are an event enrichment agent for a Kafka-based support platform.
                Analyze the support ticket and answer with JSON only.
                Do not wrap the JSON in markdown.
                Use exactly these fields:
                {
                  "category": "...",
                  "priority": "...",
                  "sentiment": "...",
                  "routingQueue": "...",
                  "shortSummary": "...",
                  "suggestedReply": "...",
                  "confidence": 0.0
                }

                Allowed values:
                - priority: LOW, MEDIUM, HIGH, CRITICAL
                - sentiment: POSITIVE, NEUTRAL, NEGATIVE, FRUSTRATED

                Ticket:
                ticketId: %s
                customerId: %s
                channel: %s
                subject: %s
                body: %s
                createdAt: %s
                """.formatted(
                ticket.ticketId(),
                ticket.customerId(),
                ticket.channel(),
                ticket.subject(),
                ticket.body(),
                ticket.createdAt()
        );
    }
}
