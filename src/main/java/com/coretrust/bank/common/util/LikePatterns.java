package com.coretrust.bank.common.util;

import java.util.Locale;

public final class LikePatterns {

    private LikePatterns() {
    }

    /** "ram" → "%ram%", with %, _ and \ escaped so they match literally. Use with escape char '\\'. */
    public static String contains(String value) {
        String escaped = value.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}