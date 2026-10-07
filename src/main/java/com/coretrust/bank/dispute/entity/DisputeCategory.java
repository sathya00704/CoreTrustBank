package com.coretrust.bank.dispute.entity;

public enum DisputeCategory {
    UNAUTHORIZED,       // customer says they didn't make it
    DUPLICATE,          // charged twice
    WRONG_AMOUNT,
    NOT_RECEIVED,       // recipient says money never arrived
    OTHER
}