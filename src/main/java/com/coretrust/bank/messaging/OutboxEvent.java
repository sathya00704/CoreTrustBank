package com.coretrust.bank.messaging;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "outbox_events",
        uniqueConstraints = @UniqueConstraint(name = "uk_outbox_events_event_id", columnNames = "event_id"),
        indexes = @Index(name = "idx_outbox_events_unpublished", columnList = "published_at, id"))
@Getter
@NoArgsConstructor
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    @Column(nullable = false, length = 60)
    private String topic;

    @Column(name = "event_key", nullable = false, length = 60)
    private String eventKey;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    /** The full JSON envelope, exactly as it will be sent */
    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(name = "last_error", length = 500)
    private String lastError;

    public OutboxEvent(String eventId, EventType type, String eventKey, String payload) {
        this.eventId = eventId;
        this.topic = type.getTopic();
        this.eventType = type.name();
        this.eventKey = eventKey;
        this.payload = payload;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public void markPublished() {
        publishedAt = Instant.now();
        lastError = null;
    }

    public void recordFailure(String error) {
        attempts++;
        lastError = (error != null && error.length() > 500) ? error.substring(0, 500) : error;
    }
}