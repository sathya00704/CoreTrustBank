package com.coretrust.bank.user.entity;

/** Extra powers an admin can grant to individual SUPPORT users. Admins implicitly have all of them. */
public enum Permission {
    VIEW_AUDIT_LOGS,
    MANAGE_USERS,
    FLAG_TRANSACTIONS;

    /** The Spring Security authority name, e.g. PERM_VIEW_AUDIT_LOGS */
    public String authority() {
        return "PERM_" + name();
    }
}