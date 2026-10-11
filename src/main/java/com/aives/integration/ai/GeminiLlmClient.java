package com.aives.integration.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Goi Gemini qua REST (generateContent), bat model tra JSON theo responseSchema.
 * API key di trong header x-goog-api-key, khong bao gio duoc ghi ra log.
 */
@Slf4j
public class GeminiLlmClient implements LlmClient {

    private final GeminiProperties properties;
    // 2 client chi khac thoi gian cho: nhanh cho hoi xoay trong luc thi, cham cho cham diem / doc file
    private final RestClient quickClient;
    private final RestClient longClient;
    // false sau khi model tu choi tham so thinkingConfig (HTTP 400): cac lan goi sau khong gui nua
    private volatile boolean sendThinkingConfig = true;

    public GeminiLlmClient(GeminiProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.quickClient = buildClient(builder.clone(), properties.getReadTimeoutMs());
        this.longClient = buildClient(builder.clone(), properties.getLongReadTimeoutMs());
    }

    private RestClient buildClient(RestClient.Builder builder, int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMs());
        factory.setReadTimeout(readTimeoutMs);
        return builder
                .baseUrl(properties.getBaseUrl())
                .requestFactory(factory)
                .defaultHeader("x-goog-api-key", properties.getApiKey().trim())
                .build();
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String generateJson(LlmRequest request) {
        Map<String, Object> thinkingConfig = sendThinkingConfig ? thinkingConfig() : null;
        try {
            return call(request, thinkingConfig);
        } catch (GeminiHttpException ex) {
            // Model khong nhan tham so "suy nghi" (moi dong model mot kieu): goi lai 1 lan khong kem tham so do
            if (ex.status == 400 && thinkingConfig != null) {
                log.warn("Model {} tu choi thinkingConfig {}, goi lai khong kem tham so nay: {}",
                        properties.getModel(), thinkingConfig, ex.getMessage());
                sendThinkingConfig = false;
                try {
                    return call(request, null);
                } catch (GeminiHttpException retryEx) {
                    throw new LlmException(retryEx.getMessage(), retryEx);
                }
            }
            throw new LlmException(ex.getMessage(), ex);
        }
    }

    /** thinkingLevel (Gemini 3) duoc uu tien; khong co thi dung thinkingBudget (Gemini 2.5); khong co ca hai thi null. */
    private Map<String, Object> thinkingConfig() {
        String level = properties.getThinkingLevel();
        if (level != null && !level.isBlank()) {
            return Map.of("thinkingLevel", level.trim());
        }
        if (properties.getThinkingBudget() >= 0) {
            return Map.of("thinkingBudget", properties.getThinkingBudget());
        }
        return null;
    }

    /** Loi HTTP tu Gemini, giu lai ma trang thai de biet co nen goi lai hay khong. */
    private static class GeminiHttpException extends RuntimeException {
        private final int status;

        GeminiHttpException(int status, String message, Throwable cause) {
            super(message, cause);
            this.status = status;
        }
    }

    private String call(LlmRequest request, Map<String, Object> thinkingConfig) {
        Map<String, Object> generationConfig = new LinkedHashMap<>();
        if (properties.getTemperature() >= 0) {
            generationConfig.put("temperature", properties.getTemperature());
        }
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.put("responseSchema", request.responseSchema());
        if (thinkingConfig != null) {
            generationConfig.put("thinkingConfig", thinkingConfig);
        }

        // File gui kem di truoc, cau lenh di sau (Gemini doc file tot hon theo thu tu nay)
        List<Map<String, Object>> parts = new ArrayList<>();
        for (LlmRequest.Attachment attachment : request.attachments()) {
            parts.add(Map.of("inlineData", Map.of(
                    "mimeType", attachment.mimeType(),
                    "data", Base64.getEncoder().encodeToString(attachment.data()))));
        }
        parts.add(Map.of("text", request.userPrompt()));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", request.systemInstruction()))));
        body.put("contents", List.of(Map.of("role", "user", "parts", parts)));
        body.put("generationConfig", generationConfig);

        JsonNode response;
        try {
            response = (request.longRunning() ? longClient : quickClient).post()
                    .uri("/models/{model}:generateContent", properties.getModel())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException ex) {
            // Vd 400 sai tham so, 403 sai key, 404 sai ten model, 429 het quota, 503 qua tai
            throw new GeminiHttpException(ex.getStatusCode().value(), "Gemini trả lỗi HTTP "
                    + ex.getStatusCode().value() + ": " + shorten(ex.getResponseBodyAsString()), ex);
        } catch (RestClientException ex) {
            // Mat mang, qua thoi gian cho...
            throw new LlmException("Không gọi được Gemini: " + ex.getMessage(), ex);
        }

        String text = extractText(response);
        if (text == null || text.isBlank()) {
            throw new LlmException("Gemini không trả nội dung: " + shorten(String.valueOf(response)));
        }
        return text;
    }

    /** Noi cac doan text trong candidates[0].content.parts. */
    private String extractText(JsonNode response) {
        if (response == null) {
            return null;
        }
        JsonNode parts = response.path("candidates").path(0).path("content").path("parts");
        StringBuilder text = new StringBuilder();
        for (JsonNode part : parts) {
            // Bo qua phan "suy nghi" neu model co tra kem
            if (!part.path("thought").asBoolean(false)) {
                text.append(part.path("text").asText(""));
            }
        }
        return text.toString();
    }

    private String shorten(String value) {
        if (value == null) {
            return "";
        }
        String oneLine = value.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= 300 ? oneLine : oneLine.substring(0, 300) + "...";
    }
}
