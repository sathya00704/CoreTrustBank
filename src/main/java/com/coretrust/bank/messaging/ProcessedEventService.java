package com.coretrust.bank.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProcessedEventService {

    private final ProcessedEventRepository repository;

    /**
     * True the first time a consumer sees an event, false for a redelivery.
     * MANDATORY: the marker must commit together with the consumer's side effects.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean markProcessed(String consumer, String eventId) {
        if (repository.existsByConsumerAndEventId(consumer, eventId)) {
            return false;
        }
        repository.save(new ProcessedEvent(consumer, eventId));
        return true;
    }
}