package com.coretrust.bank.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

import static org.springframework.http.HttpStatus.UNPROCESSABLE_CONTENT;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Validation failed"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Malformed request"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "Duplicate resource"),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "Concurrent modification"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Access denied"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Unauthorized"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid credentials"),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "Invalid token"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Token expired"),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "Account disabled"),
    SESSION_EXPIRED(HttpStatus.UNAUTHORIZED, "Session expired"),
    ACCOUNT_LIMIT_REACHED(HttpStatus.CONFLICT, "Account limit reached"),
    INVALID_ACCOUNT_STATE(HttpStatus.CONFLICT, "Invalid account state"),
    SESSION_ALREADY_ACTIVE(HttpStatus.CONFLICT, "Session already active"),
    SESSION_REVOKED(HttpStatus.UNAUTHORIZED, "Session revoked"),
    OTP_INVALID(HttpStatus.BAD_REQUEST, "Invalid OTP"),
    OTP_EXPIRED(HttpStatus.BAD_REQUEST, "OTP expired"),
    OTP_ATTEMPTS_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "Too many OTP attempts"),
    INVALID_REQUEST_STATE(HttpStatus.CONFLICT, "Invalid request state"),
    SELF_APPROVAL_NOT_ALLOWED(HttpStatus.FORBIDDEN, "Self-approval not allowed"),
    EMAIL_DELIVERY_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "Email delivery failed"),
    INSUFFICIENT_FUNDS(UNPROCESSABLE_CONTENT, "Insufficient funds"),
    INVALID_TRANSFER(HttpStatus.BAD_REQUEST, "Invalid transfer"),
    TRANSFER_LIMIT_EXCEEDED(UNPROCESSABLE_CONTENT, "Transfer limit exceeded"),
    IDEMPOTENCY_KEY_REUSED(UNPROCESSABLE_CONTENT, "Idempotency key reused"),
    ACCOUNT_SETUP_PENDING(HttpStatus.FORBIDDEN, "Account setup pending"),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "Too many requests");

    private final HttpStatus status;
    private final String title;
}
