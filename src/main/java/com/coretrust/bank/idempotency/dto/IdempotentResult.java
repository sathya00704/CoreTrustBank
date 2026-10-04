package com.coretrust.bank.idempotency.dto;

/** replayed = true when the response comes from an earlier identical request. */
public record IdempotentResult<T>(T response, boolean replayed) {
}