package com.coretrust.bank.common.util;

public final class MaskingUtils {

    private MaskingUtils() {
    }

    /** 100107389094 → XXXXXXXX9094 */
    public static String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() <= 4) {
            return accountNumber;
        }
        int visible = 4;
        return "X".repeat(accountNumber.length() - visible) + accountNumber.substring(accountNumber.length() - visible);
    }

    /** sathya@test.com → s****a@test.com */
    public static String maskEmail(String email) {
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        String masked = local.length() <= 2
                ? local.charAt(0) + "***"
                : local.charAt(0) + "****" + local.charAt(local.length() - 1);
        return masked + email.substring(at);
    }

    /** Ram Kumar → R** K**** */
    public static String maskName(String name) {
        StringBuilder masked = new StringBuilder();
        for (String word : name.trim().split("\\s+")) {
            if (!masked.isEmpty()) {
                masked.append(' ');
            }
            masked.append(word.charAt(0)).append("*".repeat(word.length() - 1));
        }
        return masked.toString();
    }
}