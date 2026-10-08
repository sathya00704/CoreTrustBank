package com.coretrust.bank.auth.entity;

public enum PasswordTokenPurpose {
    ACCOUNT_SETUP,      // first password for staff-created users
    PASSWORD_RESET      // "forgot password" (Phase 7)
}