package com.example.kafkallmagent.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SupportTicketEventJsonTest {

    @Test
    void serializesAndDeserializesInstantFields() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        SupportTicketEvent ticket = new SupportTicketEvent(
                "TCK-3003",
                "CUST-88",
                "WEB",
                "Refund status",
                "I still do not see the refund in my account.",
                Instant.parse("2026-05-09T10:00:00Z")
        );

        String json = objectMapper.writeValueAsString(ticket);
        SupportTicketEvent parsed = objectMapper.readValue(json, SupportTicketEvent.class);

        assertEquals(ticket.ticketId(), parsed.ticketId());
        assertEquals(ticket.createdAt(), parsed.createdAt());
    }
}
