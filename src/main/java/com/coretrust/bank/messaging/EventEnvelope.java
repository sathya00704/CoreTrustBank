package com.coretrust.bank.messaging;

import java.time.Instant;

/** Every message has the same outer shape; consumers dispatch on eventType and dedupe on eventId. */
public record EventEnvelope(
        String eventId,
        String eventType,
        Instant occurredAt,
        Object payload
) {
}