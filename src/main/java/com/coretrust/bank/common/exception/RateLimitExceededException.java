package com.coretrust.bank.common.exception;

import lombok.Getter;

@Getter
public class RateLimitExceededException extends BusinessException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super(ErrorCode.TOO_MANY_REQUESTS,
                "Too many attempts. Please try again in %s.".formatted(humanize(retryAfterSeconds)));
        this.retryAfterSeconds = retryAfterSeconds;
    }

    private static String humanize(long seconds) {
        if (seconds < 60) {
            return seconds + " second(s)";
        }
        return ((seconds + 59) / 60) + " minute(s)";   // round up: "1 minute" rather than "0 minutes"
    }
}