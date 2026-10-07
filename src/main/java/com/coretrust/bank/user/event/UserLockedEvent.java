package com.coretrust.bank.user.event;

public record UserLockedEvent(String name, String email, String reason) {
}