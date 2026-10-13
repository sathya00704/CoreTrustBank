package com.coretrust.bank.auth.event;

/** Carries the raw link only in memory, to the email listener. Never log or persist this event. */
public record PasswordResetRequestedEvent(String name, String email, String resetLink, long validMinutes) {
}