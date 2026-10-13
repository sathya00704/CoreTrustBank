package com.coretrust.bank.common.util;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public final class ClientIp {

    private ClientIp() {
    }

    /** The caller's IP for the current request, or "unknown" outside a request. */
    public static String current() {
        return (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)
                ? attrs.getRequest().getRemoteAddr()
                : "unknown";
    }
}