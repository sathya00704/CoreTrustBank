package com.coretrust.bank.otp.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** What the customer is consenting to. The description appears in the OTP email. */
@Getter
@RequiredArgsConstructor
public enum OtpPurpose {
    OPEN_ACCOUNT("open a new account"),
    ADD_BENEFICIARY("add a beneficiary"),
    CASH_WITHDRAWAL("withdraw cash"),
    ASSISTED_TRANSFER("make a transfer"),
    UPDATE_PHONE("update your phone number"),
    UNFREEZE_ACCOUNT("unfreeze an account"),
    CLOSE_ACCOUNT("close an account"),
    CHANGE_EMAIL("change the email address of your profile");

    private final String description;
}