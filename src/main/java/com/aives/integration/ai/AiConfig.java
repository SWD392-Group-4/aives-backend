package com.aives.integration.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Strategy Pattern - Configuration / Strategy Selector
 *
 * Chon ConcreteStrategy cho LlmClient luc khoi dong:
 * - Co ANTHROPIC_API_KEY: ClaudeLlmClient (Anthropic Claude, model CLAUDE_MODEL).
 * - Thieu key: StubLlmClient, cac chuc nang AI bao "chua bat AI" nhung app van chay.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(ClaudeProperties.class)
public class AiConfig {

    @Bean
    public LlmClient llmClient(ClaudeProperties properties, RestClient.Builder restClientBuilder) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            log.warn("[AI CHƯA BẬT] Chưa có ANTHROPIC_API_KEY: phỏng vấn sẽ không hỏi xoáy, chỉ đi lần lượt các câu hỏi gốc");
            return new StubLlmClient();
        }
        log.info("AI đã bật: Claude model {}, gọi lại tối đa {} lần khi lỗi tạm thời",
                properties.getModel(), properties.getMaxRetries());
        return new ClaudeLlmClient(properties, restClientBuilder);
    }
}
