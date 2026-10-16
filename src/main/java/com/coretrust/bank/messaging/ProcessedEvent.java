package com.coretrust.bank.messaging;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** "Consumer X has already handled event Y." The unique constraint is the final duplicate guard. */
@Entity
@Table(name = "processed_events",
        uniqueConstraints = @UniqueConstraint(name = "uk_processed_events_consumer_event",
                columnNames = {"consumer", "event_id"}))
@Getter
@NoArgsConstructor
public class ProcessedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String consumer;

    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    public ProcessedEvent(String consumer, String eventId) {
        this.consumer = consumer;
        this.eventId = eventId;
    }

    @PrePersist
    void onCreate() {
        processedAt = Instant.now();
    }
}