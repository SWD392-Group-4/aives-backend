package com.aives.integration.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Strategy Pattern - ConcreteStrategy (Anthropic Claude Implementation)
 *
 * Goi Claude qua Messages API (POST /v1/messages).
 * De nhan JSON dung schema: khai bao 1 "tool" co input_schema = schema yeu cau va ep model goi tool do
 * (tool_choice), roi lay phan "input" cua tool_use lam ket qua.
 *
 * Schema cac module nghiep vu viet theo kieu Gemini (type viet HOA: OBJECT, STRING...) nen duoc doi sang
 * JSON Schema chuan (type viet thuong) truoc khi gui.
 */
@Slf4j
public class ClaudeLlmClient implements LlmClient {

    private static final String TOOL_NAME = "submit_result";
    /** Ten truong boc ket qua khi schema goc khong phai object (input_schema cua tool bat buoc la object). */
    private static final String WRAP_FIELD = "result";
    private static final long MAX_RETRY_DELAY_MS = 10_000;
    private static final long QUICK_RETRY_DELAY_MS = 1_000;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ClaudeProperties properties;
    private final RestClient quickClient;
    private final RestClient longClient;

    public ClaudeLlmClient(ClaudeProperties properties, RestClient.Builder builder) {
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
                .defaultHeader("x-api-key", properties.getApiKey().trim())
                .defaultHeader("anthropic-version", properties.getApiVersion())
                .build();
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String generateJson(LlmRequest request) {
        int retries = Math.max(0, request.longRunning() ? properties.getMaxRetries() : properties.getQuickMaxRetries());
        int maxAttempts = 1 + retries;
        RuntimeException lastError = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return call(request);
            } catch (ClaudeHttpException ex) {
                if (!isTemporary(ex.status)) {
                    // Vd 400 sai tham so, 401 sai key, 404 sai ten model: goi lai cung khong het
                    throw new LlmException(ex.getMessage(), ex);
                }
                lastError = ex;
            } catch (ClaudeNetworkException ex) {
                if (ex.timedOut) {
                    throw new LlmException(ex.getMessage(), ex);
                }
                lastError = ex;
            }

            if (attempt < maxAttempts) {
                long delayMs = retryDelayMs(request, attempt);
                log.warn("Claude loi tam thoi (lan {}/{}), goi lai sau {} ms: {}",
                        attempt, maxAttempts, delayMs, lastError.getMessage());
                sleep(delayMs);
            }
        }

        throw new LlmException("Claude đang quá tải hoặc hết lượt gọi, đã gọi " + maxAttempts
                + " lần vẫn lỗi. Vui lòng thử lại sau ít phút. Chi tiết: " + lastError.getMessage(), lastError);
    }

    /** 429 = het luot; 5xx / 529 = may chu Anthropic qua tai. Goi lai co the het. */
    private static boolean isTemporary(int status) {
        return status == 429 || status == 500 || status == 502 || status == 503 || status == 504 || status == 529;
    }

    private long retryDelayMs(LlmRequest request, int attempt) {
        long base = Math.max(0, properties.getRetryDelayMs());
        if (!request.longRunning()) {
            return Math.min(base, QUICK_RETRY_DELAY_MS);
        }
        return Math.min(base << Math.min(attempt - 1, 10), MAX_RETRY_DELAY_MS);
    }

    private void sleep(long delayMs) {
        if (delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new LlmException("Bị dừng trong lúc chờ gọi lại Claude", ex);
        }
    }

    private String call(LlmRequest request) {
        Map<String, Object> schema = toJsonSchema(request.responseSchema());
        boolean wrapped = !"object".equals(schema.get("type"));
        Map<String, Object> inputSchema = wrapped
                ? Map.of("type", "object", "properties", Map.of(WRAP_FIELD, schema), "required", List.of(WRAP_FIELD))
                : schema;

        // File gui kem di truoc, cau lenh di sau
        List<Map<String, Object>> content = new ArrayList<>();
        for (LlmRequest.Attachment attachment : request.attachments()) {
            content.add(attachmentBlock(attachment));
        }
        content.add(Map.of("type", "text", "text", request.userPrompt()));

        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", TOOL_NAME);
        tool.put("description", "Trả kết quả cuối cùng theo đúng cấu trúc JSON yêu cầu.");
        tool.put("input_schema", inputSchema);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel().trim());
        body.put("max_tokens", request.longRunning() ? properties.getMaxTokens() : properties.getQuickMaxTokens());
        if (request.systemInstruction() != null && !request.systemInstruction().isBlank()) {
            body.put("system", request.systemInstruction());
        }
        body.put("messages", List.of(Map.of("role", "user", "content", content)));
        body.put("tools", List.of(tool));
        body.put("tool_choice", Map.of("type", "tool", "name", TOOL_NAME));

        long startedAt = System.nanoTime();
        ResponseEntity<byte[]> entity;
        try {
            entity = (request.longRunning() ? longClient : quickClient).post()
                    .uri("/v1/messages")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toEntity(byte[].class);
        } catch (RestClientResponseException ex) {
            throw new ClaudeHttpException(ex.getStatusCode().value(), "Claude trả lỗi HTTP "
                    + ex.getStatusCode().value() + ": " + shorten(ex.getResponseBodyAsString()), ex);
        } catch (RestClientException ex) {
            throw networkError(request, ex);
        }

        int status = entity.getStatusCode().value();
        byte[] raw = entity.getBody();
        if (raw == null || raw.length == 0) {
            throw new ClaudeNetworkException(false, "Claude trả về rỗng (HTTP " + status + ")", null);
        }

        JsonNode response;
        try {
            response = JSON.readTree(raw);
        } catch (IOException ex) {
            throw new LlmException("Claude trả về không phải JSON (HTTP " + status + "): "
                    + shorten(new String(raw, StandardCharsets.UTF_8)), ex);
        }

        logUsage(request, response, (System.nanoTime() - startedAt) / 1_000_000);

        if ("max_tokens".equals(response.path("stop_reason").asText())) {
            throw new LlmException("Claude trả lời bị cắt vì vượt giới hạn token (CLAUDE_MAX_TOKENS). "
                    + "Hãy giảm số câu hỏi mỗi lần hoặc tăng CLAUDE_MAX_TOKENS trong .env");
        }

        for (JsonNode block : response.path("content")) {
            if ("tool_use".equals(block.path("type").asText()) && block.has("input")) {
                JsonNode input = block.get("input");
                return (wrapped ? input.path(WRAP_FIELD) : input).toString();
            }
        }
        throw new LlmException("Claude không trả kết quả JSON: " + shorten(response.toString()));
    }

    /** PDF -> document, anh -> image, con lai doc thanh chu. */
    private static Map<String, Object> attachmentBlock(LlmRequest.Attachment attachment) {
        String mime = attachment.mimeType() == null ? "" : attachment.mimeType().toLowerCase();
        if (mime.equals("application/pdf") || mime.startsWith("image/")) {
            Map<String, Object> source = Map.of(
                    "type", "base64",
                    "media_type", mime,
                    "data", Base64.getEncoder().encodeToString(attachment.data()));
            return Map.of("type", mime.startsWith("image/") ? "image" : "document", "source", source);
        }
        return Map.of("type", "text", "text", new String(attachment.data(), StandardCharsets.UTF_8));
    }

    /**
     * Doi schema kieu Gemini sang JSON Schema chuan: type viet thuong, nullable -> ["kieu", "null"],
     * bo propertyOrdering (chi Gemini dung). Ten thuoc tinh trong "properties" giu nguyen.
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> toJsonSchema(Map<String, Object> schema) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (schema == null) {
            out.put("type", "object");
            return out;
        }
        boolean nullable = Boolean.TRUE.equals(schema.get("nullable"));
        for (Map.Entry<String, Object> e : schema.entrySet()) {
            String key = e.getKey();
            Object value = e.getValue();
            switch (key) {
                case "nullable", "propertyOrdering" -> {
                    // bo qua
                }
                case "type" -> {
                    String type = String.valueOf(value).toLowerCase();
                    out.put("type", nullable ? List.of(type, "null") : type);
                }
                case "properties" -> {
                    Map<String, Object> props = new LinkedHashMap<>();
                    ((Map<String, Object>) value).forEach((name, sub) ->
                            props.put(name, sub instanceof Map ? toJsonSchema((Map<String, Object>) sub) : sub));
                    out.put("properties", props);
                }
                case "items" -> out.put("items", value instanceof Map ? toJsonSchema((Map<String, Object>) value) : value);
                default -> out.put(key, value);
            }
        }
        return out;
    }

    private RuntimeException networkError(LlmRequest request, RestClientException ex) {
        Throwable root = ex.getMostSpecificCause();
        String detail = root.getClass().getSimpleName() + (root.getMessage() == null ? "" : ": " + root.getMessage());
        if (root instanceof SocketTimeoutException) {
            if (String.valueOf(root.getMessage()).toLowerCase().contains("connect")) {
                return new ClaudeNetworkException(false, "Không kết nối được tới Claude trong "
                        + properties.getConnectTimeoutMs() + " ms (" + detail + ")", ex);
            }
            int timeoutMs = request.longRunning() ? properties.getLongReadTimeoutMs() : properties.getReadTimeoutMs();
            return new ClaudeNetworkException(true, "Claude không trả lời trong "
                    + Math.max(1, Math.round(timeoutMs / 1000.0)) + " giây (quá thời gian chờ). Vui lòng thử lại; nếu vẫn bị thì tăng "
                    + (request.longRunning() ? "CLAUDE_LONG_TIMEOUT_MS" : "CLAUDE_TIMEOUT_MS") + " trong file .env", ex);
        }
        if (root instanceof IOException) {
            return new ClaudeNetworkException(false, detail, ex);
        }
        return new LlmException("Không gọi được Claude: " + ex.getMessage() + " | Nguyên nhân: " + detail, ex);
    }

    private void logUsage(LlmRequest request, JsonNode response, long elapsedMs) {
        JsonNode usage = response.path("usage");
        log.info("Claude {} [{}] tra loi sau {} ms: input {} token, output {} token",
                properties.getModel(),
                request.documentExtraction() ? "doc file" : request.longRunning() ? "viec nang" : "hoi xoay",
                elapsedMs,
                usage.path("input_tokens").asInt(0),
                usage.path("output_tokens").asInt(0));
    }

    private static String shorten(String value) {
        if (value == null) {
            return "";
        }
        String oneLine = value.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= 300 ? oneLine : oneLine.substring(0, 300) + "...";
    }

    private static class ClaudeHttpException extends RuntimeException {
        private final int status;

        ClaudeHttpException(int status, String message, Throwable cause) {
            super(message, cause);
            this.status = status;
        }
    }

    private static class ClaudeNetworkException extends RuntimeException {
        private final boolean timedOut;

        ClaudeNetworkException(boolean timedOut, String message, Throwable cause) {
            super(message, cause);
            this.timedOut = timedOut;
        }
    }
}
