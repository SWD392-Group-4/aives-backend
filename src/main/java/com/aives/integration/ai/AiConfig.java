package com.aives.integration.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * ============================================================================
 * DESIGN PATTERN: STRATEGY PATTERN
 * VAI TRÒ TRONG PATTERN: Client / Factory (Nơi quyết định chọn Concrete Strategy)
 * 
 * GIẢI THÍCH KHI THUYẾT TRÌNH:
 * 1. Lớp cấu hình này đóng vai trò quyết định chiến lược (Strategy) nào sẽ được khởi tạo
 *    dựa trên biến môi trường (Environment Variable: GEMINI_API_KEY).
 * 2. Nếu có API key -> Chọn GeminiLlmClient (ConcreteStrategy thực tế).
 * 3. Nếu không có API key -> Chọn StubLlmClient (ConcreteStrategy giả lập / an toàn).
 * 4. Đối tượng trả về đều có kiểu là interface `LlmClient`, sau đó được Spring
 *    tự động tiêm (Dependency Injection) vào các Context như VivaInterviewService.
 * ============================================================================
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(GeminiProperties.class)
public class AiConfig {

    @Bean
    public LlmClient llmClient(GeminiProperties properties, RestClient.Builder restClientBuilder) {
        // Kiểm tra biến môi trường xem đã có GEMINI_API_KEY hay chưa
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            log.warn("[AI CHƯA BẬT] Chưa có GEMINI_API_KEY: phòng vấn sẽ không hỏi xoáy, chỉ đi lần lượt các câu hỏi gốc");
            // Trả về Strategy Stub (không gọi mạng)
            return new StubLlmClient();
        }
        log.info("AI đã bật: Gemini model {}, model đọc file {}, model dự phòng {}, gọi lại tối đa {} lần khi lỗi tạm thời",
                properties.getModel(),
                properties.getImportModel() == null || properties.getImportModel().isBlank()
                        ? "(dùng model chính)" : properties.getImportModel(),
                properties.getFallbackModels(), properties.getMaxRetries());
        // Trả về Strategy Gemini thực tế
        return new GeminiLlmClient(properties, restClientBuilder);
    }
}
