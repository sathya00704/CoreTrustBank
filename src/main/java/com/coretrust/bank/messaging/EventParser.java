package com.coretrust.bank.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class EventParser {

    private final JsonMapper jsonMapper;

    public IncomingEvent parse(String message) {
        try {
            IncomingEvent event = jsonMapper.readValue(message, IncomingEvent.class);
            if (event.eventId() == null || event.eventType() == null || event.payload() == null) {
                throw new InvalidEventException("Event envelope is missing eventId, eventType or payload");
            }
            return event;
        } catch (JacksonException ex) {
            throw new InvalidEventException("Message is not a valid event: " + ex.getMessage(), ex);
        }
    }

    public <T> T payload(IncomingEvent event, Class<T> type) {
        try {
            return jsonMapper.treeToValue(event.payload(), type);
        } catch (JacksonException ex) {
            throw new InvalidEventException(
                    "Payload of %s does not match %s".formatted(event.eventId(), type.getSimpleName()), ex);
        }
    }
}