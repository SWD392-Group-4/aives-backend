package com.aives.integration.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Strategy Pattern - Configuration / Strategy Selector
 *
 * Thành phần cấu hình chịu trách nhiệm khởi tạo và quyết định ConcreteStrategy phù hợp
 * dựa trên thông số môi trường (Environment Properties).
 *
 * Cơ chế hoạt động:
 * - Kiểm tra sự tồn tại của GEMINI_API_KEY tại thời điểm khởi động hệ thống.
 * - Khi có API key: Khởi tạo GeminiLlmClient để kích hoạt toàn bộ khả năng xử lý AI.
 * - Khi thiếu API key: Tự động chuyển đổi sang StubLlmClient nhằm đảm bảo tính ổn định.
 * - Đối tượng được tiêm vào các Context thông qua trừu tượng chung LlmClient.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(GeminiProperties.class)
public class AiConfig {

    @Bean
    public LlmClient llmClient(GeminiProperties properties, RestClient.Builder restClientBuilder) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            log.warn("[AI CHƯA BẬT] Chưa có GEMINI_API_KEY: phòng vấn sẽ không hỏi xoáy, chỉ đi lần lượt các câu hỏi gốc");
            return new StubLlmClient();
        }
        log.info("AI đã bật: Gemini model {}, model đọc file {}, model dự phòng {}, gọi lại tối đa {} lần khi lỗi tạm thời",
                properties.getModel(),
                properties.getImportModel() == null || properties.getImportModel().isBlank()
                        ? "(dùng model chính)" : properties.getImportModel(),
                properties.getFallbackModels(), properties.getMaxRetries());
        return new GeminiLlmClient(properties, restClientBuilder);
    }
}
