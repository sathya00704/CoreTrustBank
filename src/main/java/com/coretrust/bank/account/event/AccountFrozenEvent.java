package com.coretrust.bank.account.event;

import com.coretrust.bank.user.entity.Role;

public record AccountFrozenEvent(
        String accountNumber,
        String customerName,
        String customerEmail,
        Role frozenBy,
        String reason
) {
}