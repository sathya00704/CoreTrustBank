package com.coretrust.bank.common.dto;

import java.time.Instant;

public record MessageResponse(String message, Instant timestamp) {

    public static MessageResponse of(String message) {
        return new MessageResponse(message, Instant.now());
    }
}