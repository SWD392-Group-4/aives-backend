package com.aives.modules.auth.enums;

import lombok.Getter;

/**
 * Khop voi CHECK constraint CK_users_role trong DB.
 * idPrefix la 2 chu cai dau cua users.id (vd: ST482913).
 */
@Getter
public enum Role {
    ADMIN("AD"),
    LECTURER("LE"),
    STUDENT("ST");

    private final String idPrefix;

    Role(String idPrefix) {
        this.idPrefix = idPrefix;
    }
}
