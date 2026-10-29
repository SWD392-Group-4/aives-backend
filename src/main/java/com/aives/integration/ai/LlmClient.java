package com.aives.integration.ai;

import java.util.Map;

/**
 * ============================================================================
 * DESIGN PATTERN: STRATEGY PATTERN
 * VAI TRÒ TRONG PATTERN: <<interface>> Strategy
 * 
 * GIẢI THÍCH KHI THUYẾT TRÌNH:
 * 1. Đây là Strategy Interface định nghĩa hành vi giao tiếp với AI.
 * 2. Các tầng nghiệp vụ (Context như VivaInterviewService, FollowUpDecisionService)
 *    chỉ phụ thuộc vào interface này, không bị gắn chặt (tight coupling) vào Gemini hay OpenAI.
 * 3. Tuân thủ nguyên lý DIP (Dependency Inversion Principle) và OCP (Open/Closed Principle).
 * ============================================================================
 */
public interface LlmClient {

    /** 
     * Kiểm tra xem AI hiện tại có đang được kích hoạt hay không.
     * Trả về false nếu chưa cấu hình API key.
     */
    boolean isEnabled();

    /**
     * Gọi model AI và yêu cầu trả về JSON theo schema định sẵn.
     *
     * @param request thông tin prompt, system instruction, schema
     * @return chuỗi JSON do AI trả về
     * @throws LlmException khi không gọi được hoặc AI trả về lỗi
     */
    String generateJson(LlmRequest request);

    /** 
     * Gọi nhanh không kèm file (dùng cho quyết định hỏi xoáy trong lúc thi vấn đáp).
     */
    default String generateJson(String systemInstruction, String userPrompt, Map<String, Object> responseSchema) {
        return generateJson(LlmRequest.quick(systemInstruction, userPrompt, responseSchema));
    }
}
