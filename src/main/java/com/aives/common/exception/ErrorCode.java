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
    WRONG_CURRENT_PASSWORD(HttpStatus.BAD_REQUEST, "Mật khẩu hiện tại không đúng"),
    NEW_PASSWORD_SAME_AS_CURRENT(HttpStatus.BAD_REQUEST, "Mật khẩu mới phải khác mật khẩu hiện tại"),
    EXAM_TIME_RANGE_INVALID(HttpStatus.BAD_REQUEST, "Thời gian đóng phiên phải sau thời gian mở phiên"),
    EXAM_START_IN_PAST(HttpStatus.BAD_REQUEST, "Thời gian mở phiên không được ở quá khứ"),
    EXAM_START_TOO_FAR(HttpStatus.BAD_REQUEST, "Thời gian mở phiên không được xa quá 365 ngày"),
    EXAM_WINDOW_TOO_LONG(HttpStatus.BAD_REQUEST, "Phiên thi chỉ được mở tối đa 30 ngày"),
    EXAM_DURATION_EXCEEDS_WINDOW(HttpStatus.BAD_REQUEST, "Thời lượng làm bài không được dài hơn khoảng thời gian mở phiên"),
    EXAM_CODE_INVALID_FORMAT(HttpStatus.BAD_REQUEST, "Mã phiên thi phải có dạng AIVES_EXAM_yyyy_xxxxxx"),
    EXAM_PASSCODE_INVALID_FORMAT(HttpStatus.BAD_REQUEST, "Mã truy cập gồm đúng 6 chữ số"),
    EXAM_JOIN_INVALID(HttpStatus.BAD_REQUEST, "Mã phiên thi hoặc mã truy cập không đúng"),
    EXAM_TOPIC_INVALID(HttpStatus.BAD_REQUEST, "Chủ đề không tồn tại hoặc không thuộc ngân hàng câu hỏi của bạn"),
    EXAM_NOT_ENOUGH_QUESTIONS(HttpStatus.BAD_REQUEST, "Các chủ đề đã chọn không đủ câu hỏi đã duyệt cho phiên thi"),
    IMPORT_FILE_INVALID(HttpStatus.BAD_REQUEST, "Chỉ nhận file .pdf, .docx hoặc .txt"),
    IMPORT_FILE_TOO_LARGE(HttpStatus.BAD_REQUEST, "File quá lớn, tối đa 10 MB"),
    IMPORT_NO_QUESTIONS(HttpStatus.BAD_REQUEST, "Không tìm thấy câu hỏi nào trong file"),
    IMPORT_NO_RUBRICS(HttpStatus.BAD_REQUEST, "Không tìm thấy rubric (tiêu chí chấm điểm) nào trong file"),
    GRADE_SCORE_INVALID(HttpStatus.BAD_REQUEST, "Điểm không hợp lệ"),
    GRADE_NOTE_REQUIRED(HttpStatus.BAD_REQUEST, "Điểm chốt lệch quá 2 điểm so với điểm AI gợi ý nên bắt buộc nhập ghi chú giải trình (ít nhất 5 ký tự)"),
    OTP_INVALID(HttpStatus.BAD_REQUEST, "Mã xác thực OTP không đúng"),
    OTP_EXPIRED(HttpStatus.BAD_REQUEST, "Mã xác thực OTP đã hết hạn, vui lòng gửi lại mã mới"),
    OTP_NOT_FOUND(HttpStatus.BAD_REQUEST, "Không tìm thấy yêu cầu xác thực OTP cho email này"),

    // 401 / 403
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Chưa đăng nhập hoặc token không hợp lệ"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không đúng"),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "Tài khoản đã bị khoá"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Không có quyền truy cập"),
    PASSWORD_CHANGE_REQUIRED(HttpStatus.FORBIDDEN, "Bạn đang dùng mật khẩu tạm, vui lòng đổi mật khẩu trước khi tiếp tục"),

    // 404 / 409
    NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy tài nguyên"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng"),
    EXAM_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy phiên thi"),
    EXAM_ATTEMPT_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy lượt thi"),
    QUESTION_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy câu hỏi"),
    QUESTION_FIGURE_NOT_FOUND(HttpStatus.NOT_FOUND, "Câu hỏi không có hình vẽ"),
    RUBRIC_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy rubric"),
    QUESTION_GRADE_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy điểm của câu hỏi"),
    APPEAL_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy đơn phúc khảo"),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "Email đã tồn tại"),
    DATA_CONFLICT(HttpStatus.CONFLICT, "Dữ liệu bị trùng hoặc vi phạm ràng buộc"),
    EXAM_SESSION_NOT_STARTED(HttpStatus.CONFLICT, "Phiên thi chưa đến giờ mở"),
    EXAM_SESSION_ENDED(HttpStatus.CONFLICT, "Phiên thi đã kết thúc"),
    EXAM_SESSION_CANCELLED(HttpStatus.CONFLICT, "Phiên thi đã bị huỷ"),
    EXAM_SESSION_LOCKED_FIELDS(HttpStatus.CONFLICT, "Phiên thi đã mở nên không thể đổi thời gian mở phiên, thời lượng làm bài, chủ đề và số câu hỏi"),
    EXAM_END_CANNOT_SHORTEN(HttpStatus.CONFLICT, "Phiên thi đã mở nên chỉ có thể kéo dài thời gian đóng phiên"),
    EXAM_SESSION_HAS_ATTEMPTS(HttpStatus.CONFLICT, "Phiên thi đã kết thúc và có sinh viên tham gia nên không thể xoá"),
    EXAM_MAX_ATTEMPTS_CANNOT_REDUCE(HttpStatus.CONFLICT, "Phiên thi đã mở nên chỉ có thể tăng số lượt thi tối đa"),
    EXAM_ATTEMPT_LIMIT_REACHED(HttpStatus.CONFLICT, "Bạn đã dùng hết số lượt thi của phiên thi này"),
    EXAM_ATTEMPT_NOT_RUNNING(HttpStatus.CONFLICT, "Lượt thi đã kết thúc hoặc đã hết giờ làm bài"),
    INTERVIEW_NO_QUESTIONS(HttpStatus.CONFLICT, "Phiên thi chưa có câu hỏi nào, vui lòng báo giảng viên"),
    QUESTION_RUBRIC_REQUIRED(HttpStatus.CONFLICT, "Câu hỏi phải được gắn rubric trước khi duyệt"),
    GRADING_IN_PROGRESS(HttpStatus.CONFLICT, "AI đang chấm lượt thi này, vui lòng thử lại sau ít phút"),
    GRADES_NOT_APPROVED(HttpStatus.CONFLICT, "Còn câu hỏi chưa được giảng viên duyệt điểm"),
    RESULT_ALREADY_PUBLISHED(HttpStatus.CONFLICT, "Kết quả đã công bố nên không thể sửa điểm"),
    APPEAL_ALREADY_PENDING(HttpStatus.CONFLICT, "Câu hỏi này đang có đơn phúc khảo chờ giảng viên xử lý"),
    APPEAL_ALREADY_RESOLVED(HttpStatus.CONFLICT, "Đơn phúc khảo này đã được xử lý"),
    RESULT_NOT_PUBLISHED(HttpStatus.CONFLICT, "Kết quả chưa được giảng viên công bố"),
    INTERVIEW_EXCHANGE_NOT_CURRENT(HttpStatus.CONFLICT, "Câu hỏi này đã được trả lời hoặc không còn là câu hỏi hiện tại"),

    // 429
    EXAM_JOIN_LOCKED(HttpStatus.TOO_MANY_REQUESTS, "Bạn đã nhập sai quá nhiều lần, vui lòng thử lại sau"),
    OTP_MAX_ATTEMPTS_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "Bạn đã nhập sai mã OTP quá 5 lần. Vui lòng yêu cầu mã mới"),
    OTP_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Vui lòng đợi 60 giây trước khi yêu cầu gửi lại mã OTP"),

    // 500 / 502 / 503
    AI_REQUEST_FAILED(HttpStatus.BAD_GATEWAY, "AI không xử lý được yêu cầu, vui lòng thử lại"),
    AI_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Hệ thống chưa bật AI (thiếu GEMINI_API_KEY) nên chưa dùng được chức năng này"),
    MAIL_SEND_FAILED(HttpStatus.BAD_GATEWAY, "Không gửi được email. Vui lòng kiểm tra địa chỉ email và cấu hình gửi mail rồi thử lại"),
    SPEECH_REQUEST_FAILED(HttpStatus.BAD_GATEWAY, "Không xử lý được giọng nói, vui lòng thử lại"),
    SPEECH_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Hệ thống chưa bật chuyển giọng nói (thiếu GOOGLE_SPEECH_CREDENTIALS) nên chưa dùng được chức năng này"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi hệ thống");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
