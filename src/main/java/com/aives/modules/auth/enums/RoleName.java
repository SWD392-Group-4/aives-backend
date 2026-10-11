package com.aives.modules.auth.enums;

import lombok.Getter;

/**
 * Ten cac role cua he thong, khop voi cot roles.name trong DB.
 * idPrefix la 2 chu cai dau cua users_id (vd: ST482913), dong thoi la khoa chinh roles_id cua role do.
 */
@Getter
public enum RoleName {
    ADMIN("AD", "Quản trị viên"),
    LECTURER("LE", "Giảng viên"),
    STUDENT("ST", "Sinh viên");

    private final String idPrefix;
    private final String description;

    RoleName(String idPrefix, String description) {
        this.idPrefix = idPrefix;
        this.description = description;
    }
}
