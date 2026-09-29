package com.coretrust.bank.auth.dto;

import java.time.Instant;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,                  // seconds until this access token expires
        Instant accessTokenExpiresAt,    // when to show the "extend session?" prompt (a bit before this)
        Instant sessionExpiresAt,        // fixed end of the session
        boolean extendable               // false once the one extension is used
) {
}