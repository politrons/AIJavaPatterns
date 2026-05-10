package com.example.kafkallmagent.business;

import com.example.kafkallmagent.llm.LanguageModelClient;
import com.example.kafkallmagent.model.SupportTicketEvent;
import com.example.kafkallmagent.model.TicketEnrichment;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TicketEnrichmentServiceTest {

    @Test
    void parsesStructuredJsonReturnedByTheModel() {
        LanguageModelClient fakeModel = prompt -> """
                ```json
                {
                  "category": "PAYMENTS",
                  "priority": "HIGH",
                  "sentiment": "FRUSTRATED",
                  "routingQueue": "billing-escalations",
                  "shortSummary": "Customer reports a duplicate charge.",
                  "suggestedReply": "We are reviewing the duplicate charge and will confirm the refund shortly.",
                  "confidence": 0.94
                }
                ```
                """;

        TicketEnrichmentService service = new TicketEnrichmentService(new TicketPromptFactory(), fakeModel);
        TicketEnrichment enrichment = service.enrich(sampleTicket());

        assertEquals("PAYMENTS", enrichment.category());
        assertEquals("HIGH", enrichment.priority());
        assertEquals("billing-escalations", enrichment.routingQueue());
        assertTrue(enrichment.confidence() > 0.9);
    }

    @Test
    void promptContainsTheOriginalTicketFields() {
        TicketPromptFactory promptFactory = new TicketPromptFactory();

        String prompt = promptFactory.buildPrompt(sampleTicket());

        assertTrue(prompt.contains("TCK-1001"));
        assertTrue(prompt.contains("Payment charged twice"));
        assertTrue(prompt.contains("duplicate payment"));
    }

    @Test
    void repairsTruncatedJsonBeforeParsing() {
        LanguageModelClient fakeModel = prompt -> """
                {
                  "category": "PAYMENTS",
                  "priority": "HIGH",
                  "sentiment": "FRUSTRATED",
                  "routingQueue": "billing-escalations",
                  "shortSummary": "Customer reports a duplicate charge.",
                  "suggestedReply": "We are reviewing the duplicate charge and will confirm the refund shortly.",
                  "confidence": 0.94
                """;

        TicketEnrichmentService service = new TicketEnrichmentService(new TicketPromptFactory(), fakeModel);
        TicketEnrichment enrichment = service.enrich(sampleTicket());

        assertEquals("PAYMENTS", enrichment.category());
        assertEquals("HIGH", enrichment.priority());
    }

    @Test
    void retriesWithStricterPromptWhenTheFirstResponseIsNotParsable() {
        AtomicInteger callCount = new AtomicInteger();
        LanguageModelClient fakeModel = prompt -> {
            if (callCount.getAndIncrement() == 0) {
                return "not-json-at-all";
            }
            return """
                    {
                      "category": "PAYMENTS",
                      "priority": "HIGH",
                      "sentiment": "FRUSTRATED",
                      "routingQueue": "billing-escalations",
                      "shortSummary": "Customer reports a duplicate charge.",
                      "suggestedReply": "We are reviewing the duplicate charge and will confirm the refund shortly.",
                      "confidence": 0.94
                    }
                    """;
        };

        TicketEnrichmentService service = new TicketEnrichmentService(new TicketPromptFactory(), fakeModel);
        TicketEnrichment enrichment = service.enrich(sampleTicket());

        assertEquals("PAYMENTS", enrichment.category());
        assertEquals(2, callCount.get());
    }

    private static SupportTicketEvent sampleTicket() {
        return new SupportTicketEvent(
                "TCK-1001",
                "CUST-42",
                "EMAIL",
                "Payment charged twice",
                "I was charged twice for the same order and need a refund for the duplicate payment.",
                Instant.parse("2026-05-09T08:30:00Z")
        );
    }
}
