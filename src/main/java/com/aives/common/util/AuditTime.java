package com.aives.common.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Doi created_at / updated_at cua BaseEntity sang Instant.
 *
 * 2 cot nay do Spring Data tu dien bang GIO CUA MAY CHU (vd gio Viet Nam), khac voi cac moc thoi gian
 * nghiep vu (started_at, completed_at, graded_at...) duoc luu theo gio UTC. Doi nham bang UTC thi gio hien
 * tren web se lech dung bang mui gio cua may chu (o Viet Nam la lech 7 tieng).
 */
public final class AuditTime {

    private AuditTime() {
    }

    public static Instant toInstant(LocalDateTime serverLocalDateTime) {
        return serverLocalDateTime == null ? null : serverLocalDateTime.atZone(ZoneId.systemDefault()).toInstant();
    }
}
