package com.aives.modules.exam.enums;

/**
 * Trang thai phien thi. Chi CANCELLED duoc luu trong DB (cot cancelled_at),
 * 3 trang thai con lai duoc TINH tu thoi gian hien tai so voi start_at / end_at
 * nen khong can job chay nen de cap nhat.
 */
public enum ExamSessionStatus {
    UPCOMING,   // chua den gio mo
    ONGOING,    // dang mo, sinh vien vao duoc
    ENDED,      // da qua gio dong
    CANCELLED   // nguoi tao da huy
}
