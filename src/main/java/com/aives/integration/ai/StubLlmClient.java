package com.aives.integration.ai;

/**
 * Strategy Pattern - ConcreteStrategy (Fallback / Offline Implementation)
 *
 * Chiến lược dự phòng khi hệ thống chạy trong môi trường phát triển ngoại tuyến
 * hoặc chưa được cấu hình GEMINI_API_KEY.
 *
 * Đặc điểm kiến trúc:
 * - Thay vì làm gián đoạn luồng thực thi của toàn hệ thống, lớp này trả về cờ
 *   trạng thái không khả dụng hoặc ném ngoại lệ có kiểm soát (LlmException).
 * - Tầng nghiệp vụ (Context) bắt ngoại lệ này để áp dụng cơ chế suy thoái an toàn
 *   (Graceful Degradation), cho phép bài thi tiếp tục bình thường với bộ câu hỏi mặc định.
 */
public class StubLlmClient implements LlmClient {

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public String generateJson(LlmRequest request) {
        throw new LlmException("Chưa cấu hình GEMINI_API_KEY nên không gọi được AI");
    }
}
