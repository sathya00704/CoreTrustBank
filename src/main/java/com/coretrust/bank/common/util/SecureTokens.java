package com.coretrust.bank.common.util;

import java.security.SecureRandom;
import java.util.Base64;

public final class SecureTokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    private SecureTokens() {
    }

    /** Unguessable, URL-safe random string, e.g. for links and one-time tokens. */
    public static String urlSafe(int bytes) {
        byte[] buffer = new byte[bytes];
        RANDOM.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }
}