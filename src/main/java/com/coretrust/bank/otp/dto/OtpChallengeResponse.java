package com.coretrust.bank.otp.dto;

import java.time.Instant;

/** Returned to staff after an OTP is sent. Never contains the code itself. */
public record OtpChallengeResponse(
        Long otpId,
        String sentTo,       // masked, e.g. s****a@test.com
        Instant expiresAt
) {
}