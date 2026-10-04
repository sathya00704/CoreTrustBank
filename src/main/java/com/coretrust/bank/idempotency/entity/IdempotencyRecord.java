package com.coretrust.bank.idempotency.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "idempotency_records",
        uniqueConstraints = @UniqueConstraint(name = "uk_idempotency_user_key",
                columnNames = {"user_id", "idempotency_key"}))
@Getter
@NoArgsConstructor
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Keys are scoped per user: two users may use the same key independently */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(nullable = false, length = 30)
    private String operation;

    /** SHA-256 of the operation + request body: detects key reuse with a different request */
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    /** Set in the same transaction that creates the money movement */
    @Setter
    @Column(name = "transaction_id")
    private Long transactionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public IdempotencyRecord(Long userId, String idempotencyKey, String operation, String requestHash) {
        this.userId = userId;
        this.idempotencyKey = idempotencyKey;
        this.operation = operation;
        this.requestHash = requestHash;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}