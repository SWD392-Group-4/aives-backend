package com.aives.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    // 400
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Dữ liệu gửi lên không hợp lệ"),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "Yêu cầu không hợp lệ"),
    CANNOT_DELETE_SELF(HttpStatus.BAD_REQUEST, "Không thể tự xoá tài khoản đang đăng nhập"),
    CANNOT_DISABLE_SELF(HttpStatus.BAD_REQUEST, "Không thể tự vô hiệu hoá tài khoản đang đăng nhập"),
    STUDENT_CODE_REQUIRED(HttpStatus.BAD_REQUEST, "Sinh viên bắt buộc phải có mã sinh viên"),
    WRONG_CURRENT_PASSWORD(HttpStatus.BAD_REQUEST, "Mật khẩu hiện tại không đúng"),

    // 401 / 403
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Chưa đăng nhập hoặc token không hợp lệ"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không đúng"),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "Tài khoản đã bị khoá"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Không có quyền truy cập"),

    // 404 / 409
    NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy tài nguyên"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng"),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "Email đã tồn tại"),
    STUDENT_CODE_ALREADY_EXISTS(HttpStatus.CONFLICT, "Mã sinh viên đã tồn tại"),
    DATA_CONFLICT(HttpStatus.CONFLICT, "Dữ liệu bị trùng hoặc vi phạm ràng buộc"),

    // 500
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi hệ thống");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
