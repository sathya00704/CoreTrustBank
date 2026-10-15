package com.coretrust.bank.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository repository;
    private final JsonMapper jsonMapper;

    /** MANDATORY: the event must commit together with the business change that caused it. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(EventType type, Object key, Object payload) {
        repository.save(build(type, key, payload));
    }

    /** For failures: the caller's transaction rolls back, but the "it failed" event must survive. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependently(EventType type, Object key, Object payload) {
        repository.save(build(type, key, payload));
    }

    private OutboxEvent build(EventType type, Object key, Object payload) {
        String eventId = UUID.randomUUID().toString();
        EventEnvelope envelope = new EventEnvelope(eventId, type.name(), Instant.now(), payload);
        return new OutboxEvent(eventId, type, String.valueOf(key), jsonMapper.writeValueAsString(envelope));
    }
}