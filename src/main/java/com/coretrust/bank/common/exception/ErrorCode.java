package com.coretrust.bank.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

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
    SESSION_REVOKED(HttpStatus.UNAUTHORIZED, "Session revoked");

    private final HttpStatus status;
    private final String title;
}
