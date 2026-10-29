package com.aives.integration.ai;

/**
 * ============================================================================
 * DESIGN PATTERN: STRATEGY PATTERN
 * VAI TRÒ TRONG PATTERN: ConcreteStrategy (Thuật toán thay thế / Fallback)
 * 
 * GIẢI THÍCH KHI THUYẾT TRÌNH:
 * 1. Đây là Concrete Strategy đại diện cho kịch bản chạy offline hoặc khi chưa có API key.
 * 2. Thay vì làm hệ thống bị crash, StubLlmClient trả về false cho isEnabled()
 *    hoặc ném LlmException được kiểm soát.
 * 3. Tầng nghiệp vụ (Context) sẽ bắt ngoại lệ này và tự động chuyển câu hỏi tiếp theo,
 *    giúp phòng thi vẫn tiếp tục diễn ra bình thường (Graceful Degradation).
 * ============================================================================
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
