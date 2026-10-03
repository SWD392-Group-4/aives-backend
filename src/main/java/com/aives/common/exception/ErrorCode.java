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
    EXAM_TIME_RANGE_INVALID(HttpStatus.BAD_REQUEST, "Thời gian đóng phiên phải sau thời gian mở phiên"),
    EXAM_START_IN_PAST(HttpStatus.BAD_REQUEST, "Thời gian mở phiên không được ở quá khứ"),
    EXAM_START_TOO_FAR(HttpStatus.BAD_REQUEST, "Thời gian mở phiên không được xa quá 365 ngày"),
    EXAM_WINDOW_TOO_LONG(HttpStatus.BAD_REQUEST, "Phiên thi chỉ được mở tối đa 30 ngày"),
    EXAM_DURATION_EXCEEDS_WINDOW(HttpStatus.BAD_REQUEST, "Thời lượng làm bài không được dài hơn khoảng thời gian mở phiên"),
    EXAM_CODE_INVALID_FORMAT(HttpStatus.BAD_REQUEST, "Mã phiên thi phải có dạng AIVES_EXAM_yyyy_xxxxxx"),
    EXAM_PASSCODE_INVALID_FORMAT(HttpStatus.BAD_REQUEST, "Mã truy cập gồm đúng 6 chữ số"),
    EXAM_JOIN_INVALID(HttpStatus.BAD_REQUEST, "Mã phiên thi hoặc mã truy cập không đúng"),

    // 401 / 403
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Chưa đăng nhập hoặc token không hợp lệ"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không đúng"),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "Tài khoản đã bị khoá"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Không có quyền truy cập"),

    // 404 / 409
    NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy tài nguyên"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng"),
    EXAM_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy phiên thi"),
    EXAM_ATTEMPT_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy lượt thi"),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "Email đã tồn tại"),
    STUDENT_CODE_ALREADY_EXISTS(HttpStatus.CONFLICT, "Mã sinh viên đã tồn tại"),
    DATA_CONFLICT(HttpStatus.CONFLICT, "Dữ liệu bị trùng hoặc vi phạm ràng buộc"),
    EXAM_SESSION_NOT_STARTED(HttpStatus.CONFLICT, "Phiên thi chưa đến giờ mở"),
    EXAM_SESSION_ENDED(HttpStatus.CONFLICT, "Phiên thi đã kết thúc"),
    EXAM_SESSION_CANCELLED(HttpStatus.CONFLICT, "Phiên thi đã bị huỷ"),
    EXAM_SESSION_LOCKED_FIELDS(HttpStatus.CONFLICT, "Phiên thi đã mở nên không thể đổi thời gian mở phiên và thời lượng làm bài"),
    EXAM_END_CANNOT_SHORTEN(HttpStatus.CONFLICT, "Phiên thi đã mở nên chỉ có thể kéo dài thời gian đóng phiên"),
    EXAM_SESSION_HAS_ATTEMPTS(HttpStatus.CONFLICT, "Phiên thi đã kết thúc và có sinh viên tham gia nên không thể xoá"),
    EXAM_ATTEMPT_FINISHED(HttpStatus.CONFLICT, "Bạn đã hết thời gian làm bài của phiên thi này"),

    // 429
    EXAM_JOIN_LOCKED(HttpStatus.TOO_MANY_REQUESTS, "Bạn đã nhập sai quá nhiều lần, vui lòng thử lại sau"),

    // 500
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi hệ thống");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
