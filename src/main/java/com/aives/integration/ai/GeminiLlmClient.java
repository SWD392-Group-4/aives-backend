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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * ============================================================================
 * DESIGN PATTERN: STRATEGY PATTERN
 * VAI TRÒ TRONG PATTERN: ConcreteStrategy (Chiến lược gọi API thật)
 * 
 * GIẢI THÍCH KHI THUYẾT TRÌNH:
 * 1. Đây là Concrete Strategy thực tế kết nối trực tiếp với Google Gemini qua REST API.
 * 2. Cài đặt đầy đủ các phương thức được định nghĩa trong Strategy interface LlmClient.
 * 3. Tự đóng gói logic xử lý gọi lại (retry), luân chuyển model dự phòng (fallback models)
 *    mà không làm ảnh hưởng đến các tầng nghiệp vụ bên trên.
 * ============================================================================
 */
@Slf4j
public class GeminiLlmClient implements LlmClient {

    /** Cho toi da giua 2 lan goi lai, de 1 request khong bi treo qua lau. */
    private static final long MAX_RETRY_DELAY_MS = 10_000;
    /** Luc dang thi (hoi xoay) chi cho ngan, sinh vien dang doi cau hoi tiep theo. */
    private static final long QUICK_RETRY_DELAY_MS = 1_000;
    /** Chi dung de doc JSON Gemini tra ve, khong can cau hinh gi rieng. */
    private static final ObjectMapper JSON = new ObjectMapper();

    private final GeminiProperties properties;
    // 2 client chi khac thoi gian cho: nhanh cho hoi xoay trong luc thi, cham cho cham diem / doc file
    private final RestClient quickClient;
    private final RestClient longClient;
    // Model chinh dung dau, sau do la cac model du phong (da bo trung, bo rong)
    private final List<String> models;
    // Rieng viec doc file: model doc file (neu co) dung dau, roi toi chuoi model o tren
    private final List<String> importModels;
    // Cac model da tu choi tham so thinkingConfig (HTTP 400): cac lan goi sau khong gui cho model do nua
    private final Set<String> modelsWithoutThinking = ConcurrentHashMap.newKeySet();

    public GeminiLlmClient(GeminiProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.quickClient = buildClient(builder.clone(), properties.getReadTimeoutMs());
        this.longClient = buildClient(builder.clone(), properties.getLongReadTimeoutMs());
        this.models = modelChain(properties);
        this.importModels = importChain(properties.getImportModel(), models);
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

    private static List<String> modelChain(GeminiProperties properties) {
        List<String> chain = new ArrayList<>();
        chain.add(properties.getModel().trim());
        if (properties.getFallbackModels() != null) {
            for (String fallback : properties.getFallbackModels()) {
                if (fallback != null && !fallback.isBlank() && !chain.contains(fallback.trim())) {
                    chain.add(fallback.trim());
                }
            }
        }
        return List.copyOf(chain);
    }

    private static List<String> importChain(String importModel, List<String> models) {
        if (importModel == null || importModel.isBlank()) {
            return models;
        }
        List<String> chain = new ArrayList<>();
        chain.add(importModel.trim());
        for (String model : models) {
            if (!chain.contains(model)) {
                chain.add(model);
            }
        }
        return List.copyOf(chain);
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String generateJson(LlmRequest request) {
        int retries = Math.max(0, request.longRunning() ? properties.getMaxRetries() : properties.getQuickMaxRetries());
        RuntimeException lastTemporaryError = null;
        // Mo ta ngan cua loi tam thoi gan nhat, vd "HTTP 503" hoac "mat ket noi"
        String lastProblem = "";
        boolean lastWasNetwork = false;
        int calls = 0;
        // Doc file: thu model doc file (neu co) truoc, loi gi cung chuyen sang model chinh roi model du phong
        List<String> models = request.documentExtraction() ? importModels : this.models;
        String mainModel = this.models.get(0);

        for (int modelIndex = 0; modelIndex < models.size(); modelIndex++) {
            String model = models.get(modelIndex);
            boolean primary = modelIndex == 0;
            // Model dau chuoi duoc goi lai nhieu lan; moi model sau chi thu 1 lan
            int maxAttempts = primary ? 1 + retries : 1;

            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                calls++;
                try {
                    String json = callModel(request, model);
                    if (!primary) {
                        log.warn("Model {} dang loi, da dung model {}", models.get(0), model);
                    }
                    return json;
                } catch (GeminiHttpException ex) {
                    if (!isTemporary(ex.status)) {
                        if (model.equals(mainModel)) {
                            // Vd 400 sai tham so, 403 sai key, 404 sai ten model: goi lai cung khong het
                            throw new LlmException(ex.getMessage(), ex);
                        }
                        // Model doc file / du phong cau hinh sai (vd sai ten): bo qua, thu model ke tiep
                        log.warn("Bo qua model {}: {}", model, ex.getMessage());
                        break;
                    }
                    lastTemporaryError = ex;
                    lastProblem = "HTTP " + ex.status;
                    lastWasNetwork = false;
                } catch (GeminiNetworkException ex) {
                    if (ex.timedOut) {
                        // Da cho het thoi gian cho phep ma Gemini chua tra loi: goi lai chi bat nguoi dung doi them
                        throw new LlmException(ex.getMessage(), ex);
                    }
                    lastTemporaryError = ex;
                    lastProblem = "mat ket noi";
                    lastWasNetwork = true;
                }

                if (attempt < maxAttempts) {
                    long delayMs = retryDelayMs(request, attempt);
                    log.warn("Gemini model {} loi tam thoi {} (lan {}/{}), goi lai sau {} ms: {}",
                            model, lastProblem, attempt, maxAttempts, delayMs, lastTemporaryError.getMessage());
                    sleep(delayMs);
                } else {
                    log.warn("Gemini model {} van loi tam thoi {} sau {} lan goi: {}",
                            model, lastProblem, maxAttempts, lastTemporaryError.getMessage());
                }
            }
        }

        // Model chinh luon nam trong chuoi va chi toi duoc day khi no loi tam thoi, nen lastTemporaryError khac null
        if (lastWasNetwork) {
            throw new LlmException("Kết nối tới Gemini bị ngắt giữa chừng, đã gọi " + calls + " lần với "
                    + String.join(", ", models) + " vẫn lỗi. Kiểm tra mạng của máy chạy backend rồi thử lại. Chi tiết: "
                    + lastTemporaryError.getMessage(), lastTemporaryError);
        }
        throw new LlmException("Gemini đang quá tải hoặc hết lượt gọi (" + lastProblem
                + "), đã gọi " + calls + " lần với " + String.join(", ", models)
                + " vẫn lỗi. Vui lòng thử lại sau ít phút. Chi tiết: " + lastTemporaryError.getMessage(),
                lastTemporaryError);
    }

    /** 429 = het luot goi trong phut / ngay; 5xx = may chu Gemini qua tai hoac loi. Nhung loi nay goi lai co the het. */
    private static boolean isTemporary(int status) {
        return status == 429 || status == 500 || status == 502 || status == 503 || status == 504;
    }

    /** Viec nang cho lau dan (vd 2s, 4s, 8s); hoi xoay luc dang thi chi cho toi da 1s. */
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
            throw new LlmException("Bị dừng trong lúc chờ gọi lại Gemini", ex);
        }
    }

    /** Goi 1 model. Model tu choi thinkingConfig (HTTP 400) thi goi lai ngay 1 lan khong kem tham so do. */
    private String callModel(LlmRequest request, String model) {
        Map<String, Object> thinkingConfig = modelsWithoutThinking.contains(model) ? null : thinkingConfig();
        try {
            return call(request, model, thinkingConfig);
        } catch (GeminiHttpException ex) {
            // Moi dong model nhan tham so "suy nghi" mot kieu
            if (ex.status == 400 && thinkingConfig != null) {
                log.warn("Model {} tu choi thinkingConfig {}, goi lai khong kem tham so nay: {}",
                        model, thinkingConfig, ex.getMessage());
                modelsWithoutThinking.add(model);
                return call(request, model, null);
            }
            throw ex;
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

    /**
     * Loi mang khi goi Gemini: chua nhan duoc tra loi tron ven (khong co ma HTTP).
     * timedOut = true: da cho het thoi gian ma Gemini chua tra loi; false: ket noi bi ngat.
     */
    private static class GeminiNetworkException extends RuntimeException {
        private final boolean timedOut;

        GeminiNetworkException(boolean timedOut, String message, Throwable cause) {
            super(message, cause);
            this.timedOut = timedOut;
        }
    }

    private String call(LlmRequest request, String model, Map<String, Object> thinkingConfig) {
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

        long startedAt = System.nanoTime();
        // Doc tho thanh byte[] roi tu doc JSON: de Spring doc thang ra JsonNode thi khi loi chi nhan duoc cau
        // "Error while extracting response..." ma khong biet Gemini thuc su tra ve gi.
        ResponseEntity<byte[]> entity;
        try {
            entity = (request.longRunning() ? longClient : quickClient).post()
                    .uri("/models/{model}:generateContent", model)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toEntity(byte[].class);
        } catch (RestClientResponseException ex) {
            // Vd 400 sai tham so, 403 sai key, 404 sai ten model, 429 het quota, 503 qua tai
            throw new GeminiHttpException(ex.getStatusCode().value(), "Gemini trả lỗi HTTP "
                    + ex.getStatusCode().value() + ": " + shorten(ex.getResponseBodyAsString()), ex);
        } catch (RestClientException ex) {
            // Khong nhan duoc tra loi tron ven: qua thoi gian cho, dut ket noi, khong vao duoc mang...
            throw networkError(request, ex);
        }

        int status = entity.getStatusCode().value();
        byte[] raw = entity.getBody();
        if (raw == null || raw.length == 0) {
            // Bao thanh cong nhung khong co noi dung: thuong do ket noi bi cat som, goi lai co the het
            throw new GeminiNetworkException(false, "Gemini trả về rỗng (HTTP " + status + ")", null);
        }

        JsonNode response;
        try {
            response = JSON.readTree(raw);
        } catch (IOException ex) {
            // Thuong la trang HTML cua proxy / wifi / phan mem chan mang chu khong phai Gemini
            throw new LlmException("Gemini trả về không phải JSON (HTTP " + status + ", Content-Type: "
                    + entity.getHeaders().getContentType() + "): "
                    + shorten(new String(raw, StandardCharsets.UTF_8)), ex);
        }

        logUsage(request, model, response, (System.nanoTime() - startedAt) / 1_000_000);

        String text = extractText(response);
        if (text == null || text.isBlank()) {
            throw new LlmException("Gemini không trả nội dung: " + shorten(String.valueOf(response)));
        }
        return text;
    }

    /** Doi loi cua RestClient thanh loi de hieu, kem nguyen nhan goc (cau cua Spring khong noi len duoc gi). */
    private RuntimeException networkError(LlmRequest request, RestClientException ex) {
        Throwable root = ex.getMostSpecificCause();
        String detail = root.getClass().getSimpleName() + (root.getMessage() == null ? "" : ": " + root.getMessage());

        if (root instanceof SocketTimeoutException) {
            // "Connect timed out" = chua noi duoc toi may chu (goi lai duoc); con lai la noi duoc nhung cho tra loi qua lau
            if (String.valueOf(root.getMessage()).toLowerCase().contains("connect")) {
                return new GeminiNetworkException(false, "Không kết nối được tới Gemini trong "
                        + properties.getConnectTimeoutMs() + " ms (" + detail + ")", ex);
            }
            int timeoutMs = request.longRunning() ? properties.getLongReadTimeoutMs() : properties.getReadTimeoutMs();
            return new GeminiNetworkException(true, "Gemini không trả lời trong " + Math.max(1, Math.round(timeoutMs / 1000.0))
                    + " giây (quá thời gian chờ). Vui lòng thử lại; nếu vẫn bị thì tăng "
                    + (request.longRunning() ? "GEMINI_LONG_TIMEOUT_MS" : "GEMINI_TIMEOUT_MS") + " trong file .env", ex);
        }
        if (root instanceof IOException) {
            // Vd "Unexpected end of file from server", "Connection reset", khong phan giai duoc ten mien
            return new GeminiNetworkException(false, detail, ex);
        }
        return new LlmException("Không gọi được Gemini: " + ex.getMessage() + " | Nguyên nhân: " + detail, ex);
    }

    /**
     * Ghi thoi gian va so token cua moi lan goi, de biet cham o dau: prompt qua dai (file lon),
     * tra loi qua dai (nhieu cau hoi), hay model "suy nghi" qua lau (thoughts cao -> ha GEMINI_THINKING_LEVEL).
     */
    private void logUsage(LlmRequest request, String model, JsonNode response, long elapsedMs) {
        JsonNode usage = response.path("usageMetadata");
        log.info("Gemini {} [{}] tra loi sau {} ms: prompt {} token, tra loi {} token, suy nghi {} token",
                model,
                request.documentExtraction() ? "doc file" : request.longRunning() ? "viec nang" : "hoi xoay",
                elapsedMs,
                usage.path("promptTokenCount").asInt(0),
                usage.path("candidatesTokenCount").asInt(0),
                usage.path("thoughtsTokenCount").asInt(0));
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
