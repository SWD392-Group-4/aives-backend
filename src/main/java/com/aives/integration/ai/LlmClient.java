package com.aives.integration.ai;

import java.util.Map;

/**
 * Strategy Pattern - <<interface>> Strategy
 *
 * Cổng giao tiếp chuẩn với mô hình ngôn ngữ lớn (LLM).
 * Định nghĩa hợp đồng chung cho việc tương tác với các dịch vụ AI trong hệ thống.
 * 
 * Nguyên lý thiết kế:
 * - Dependency Inversion Principle (DIP): Các module nghiệp vụ phụ thuộc vào abstraction,
 *   không phụ thuộc trực tiếp vào SDK hoặc API của bên thứ ba.
 * - Open/Closed Principle (OCP): Cho phép tích hợp thêm nhà cung cấp AI mới (OpenAI, Anthropic)
 *   bằng cách thêm class cài đặt mới mà không cần chỉnh sửa mã nguồn nghiệp vụ hiện tại.
 */
public interface LlmClient {

    /** 
     * Kiểm tra trạng thái sẵn sàng của dịch vụ AI.
     * @return true nếu đã cấu hình API key hợp lệ, ngược lại false.
     */
    boolean isEnabled();

    /**
     * Gửi yêu cầu đến mô hình AI và nhận kết quả phản hồi theo cấu trúc JSON định sẵn.
     *
     * @param request thông tin prompt, system instruction và cấu trúc dữ liệu mong muốn
     * @return chuỗi JSON do AI phản hồi theo schema
     * @throws LlmException khi gặp sự cố mạng hoặc lỗi từ nhà cung cấp dịch vụ
     */
    String generateJson(LlmRequest request);

    /** 
     * Phương thức tiện ích hỗ trợ gọi AI nhanh cho các tác vụ hỏi đáp đơn lẻ.
     */
    default String generateJson(String systemInstruction, String userPrompt, Map<String, Object> responseSchema) {
        return generateJson(LlmRequest.quick(systemInstruction, userPrompt, responseSchema));
    }
}
