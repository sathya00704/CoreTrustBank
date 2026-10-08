package com.coretrust.bank.user.dto;

import com.coretrust.bank.account.dto.AccountResponse;

public record AssistedRegistrationResponse(
        UserAdminResponse customer,
        AccountResponse account,        // null if no accountType was given
        String setupLinkSentTo          // masked email
) {
}