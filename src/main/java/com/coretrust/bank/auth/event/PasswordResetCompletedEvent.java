package com.coretrust.bank.auth.event;

public record PasswordResetCompletedEvent(String name, String email) {
}