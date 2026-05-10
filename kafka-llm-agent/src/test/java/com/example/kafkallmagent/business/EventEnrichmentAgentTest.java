package com.example.kafkallmagent.business;

import com.example.kafkallmagent.llm.LanguageModelClient;
import com.example.kafkallmagent.model.EnrichedSupportTicketEvent;
import com.example.kafkallmagent.model.SupportTicketEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EventEnrichmentAgentTest {

    @Test
    void wrapsTheOriginalTicketAndTheGeneratedEnrichment() {
        LanguageModelClient fakeModel = prompt -> """
                {
                  "category": "ACCESS",
                  "priority": "MEDIUM",
                  "sentiment": "NEGATIVE",
                  "routingQueue": "identity-support",
                  "shortSummary": "Customer cannot access the account.",
                  "suggestedReply": "Please reset your password using the secure link we sent.",
                  "confidence": 0.81
                }
                """;

        TicketEnrichmentService service = new TicketEnrichmentService(new TicketPromptFactory(), fakeModel);
        EventEnrichmentAgent agent = new EventEnrichmentAgent(service);

        EnrichedSupportTicketEvent result = agent.process(new SupportTicketEvent(
                "TCK-2002",
                "CUST-77",
                "CHAT",
                "Cannot access account",
                "My login stopped working after I changed my email address.",
                Instant.parse("2026-05-09T09:15:00Z")
        ));

        assertEquals("TCK-2002", result.originalTicket().ticketId());
        assertEquals("ACCESS", result.enrichment().category());
        assertEquals("identity-support", result.enrichment().routingQueue());
        assertNotNull(result.processedAt());
    }
}
