package com.coretrust.bank.messaging;

/** A message that can never be processed (bad JSON, missing fields). Never retried. */
public class InvalidEventException extends RuntimeException {

    public InvalidEventException(String message) {
        super(message);
    }

    public InvalidEventException(String message, Throwable cause) {
        super(message, cause);
    }
}