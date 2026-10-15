package com.coretrust.bank.messaging;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EventType {
    TRANSFER_COMPLETED(EventType.TOPIC_TRANSFERS),
    TRANSFER_FAILED(EventType.TOPIC_TRANSFERS),
    ACCOUNT_CREATED(EventType.TOPIC_ACCOUNTS),
    ACCOUNT_FROZEN(EventType.TOPIC_ACCOUNTS);

    public static final String TOPIC_TRANSFERS = "coretrust.transfers";
    public static final String TOPIC_ACCOUNTS = "coretrust.accounts";

    private final String topic;
}