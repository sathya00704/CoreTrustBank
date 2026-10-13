package com.coretrust.bank.user.event;

public record EmailChangedEvent(String name, String oldEmail, String newEmail) {
}