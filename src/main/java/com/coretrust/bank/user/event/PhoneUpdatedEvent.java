package com.coretrust.bank.user.event;

public record PhoneUpdatedEvent(
        String name,
        String email,
        String oldPhoneMasked,
        String newPhoneMasked,
        String updatedBy
) {
}