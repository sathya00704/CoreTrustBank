package com.coretrust.bank.messaging;

import tools.jackson.databind.JsonNode;

import java.time.Instant;

/** The envelope as received; the payload stays raw until a consumer asks for a specific type. */
public record IncomingEvent(
        String eventId,
        String eventType,
        Instant occurredAt,
        JsonNode payload
) {
}