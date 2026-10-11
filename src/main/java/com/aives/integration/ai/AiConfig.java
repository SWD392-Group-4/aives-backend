package com.aives.integration.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** Chon LlmClient: co GEMINI_API_KEY thi goi Gemini that, khong co thi dung ban gia (luon bao loi). */
@Slf4j
@Configuration
@EnableConfigurationProperties(GeminiProperties.class)
public class AiConfig {

    @Bean
    public LlmClient llmClient(GeminiProperties properties, RestClient.Builder restClientBuilder) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            log.warn("[AI CHUA BAT] Chua co GEMINI_API_KEY: phong van se khong hoi xoay, chi di lan luot cac cau hoi goc");
            return new StubLlmClient();
        }
        log.info("AI da bat: Gemini model {}", properties.getModel());
        return new GeminiLlmClient(properties, restClientBuilder);
    }
}
