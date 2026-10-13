package com.aives.integration.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/** Kiem tra goi lai + model du phong + loi mang cua GeminiLlmClient bang 1 may chu Gemini gia chay tren may. */
class GeminiLlmClientRetryTest {

    private static final String OK = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\\"ok\\\":true}\"}]}}]}";
    private static final String BUSY = "{\"error\":{\"code\":503,\"status\":\"UNAVAILABLE\"}}";
    /** Ma gia (khong phai ma HTTP): may chu ngat ket noi ma khong tra loi gi. */
    private static final int DROP = -1;
    /** Ma gia: may chu im lang lau hon thoi gian cho cua client. */
    private static final int SLOW = -2;
    /** Ma gia: tra HTTP 200 nhung noi dung la trang HTML, khong co Content-Type (kieu proxy / wifi chan). */
    private static final int HTML = -3;

    private HttpServer server;
    /** Ten model cua tung lan goi, theo thu tu. */
    private final List<String> calledModels = new CopyOnWriteArrayList<>();
    /** (ten model, lan goi thu may cua model do) -> ma HTTP tra ve. */
    private volatile BiFunction<String, Integer, Integer> statusFor = (model, call) -> 200;

    @BeforeEach
    void startFakeGemini() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String model = path.substring(path.lastIndexOf('/') + 1).replace(":generateContent", "");
            calledModels.add(model);
            int callNo = (int) calledModels.stream().filter(model::equals).count();
            int status = statusFor.apply(model, callNo);
            exchange.getRequestBody().readAllBytes();
            if (status == DROP || status == SLOW) {
                if (status == SLOW) {
                    sleepQuietly(1_000);
                }
                exchange.close();
                return;
            }
            if (status == HTML) {
                byte[] page = "<html>Blocked by proxy</html>".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, page.length);
                exchange.getResponseBody().write(page);
                exchange.close();
                return;
            }
            byte[] body = (status == 200 ? OK : BUSY).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopFakeGemini() {
        server.stop(0);
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private GeminiProperties properties(String... fallbackModels) {
        GeminiProperties properties = new GeminiProperties();
        properties.setApiKey("test-key");
        properties.setModel("main");
        properties.setFallbackModels(new ArrayList<>(List.of(fallbackModels)));
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setRetryDelayMs(1);
        return properties;
    }

    private GeminiLlmClient client(String... fallbackModels) {
        return new GeminiLlmClient(properties(fallbackModels), RestClient.builder());
    }

    private static LlmRequest grading() {
        return LlmRequest.longRunning("system", "prompt", Map.of("type", "object"));
    }

    @Test
    void retriesMainModelWhenBusyThenSucceeds() {
        statusFor = (model, call) -> call <= 2 ? 503 : 200;

        assertThat(client("backup").generateJson(grading())).isEqualTo("{\"ok\":true}");
        assertThat(calledModels).containsExactly("main", "main", "main");
    }

    @Test
    void usesFallbackModelWhenMainModelStaysBusy() {
        statusFor = (model, call) -> model.equals("main") ? 503 : 200;

        assertThat(client("backup").generateJson(grading())).isEqualTo("{\"ok\":true}");
        // 1 lan dau + 3 lan goi lai model chinh, roi moi sang model du phong
        assertThat(calledModels).containsExactly("main", "main", "main", "main", "backup");
    }

    @Test
    void quickRequestRetriesOnlyOnceBeforeFallback() {
        statusFor = (model, call) -> model.equals("main") ? 429 : 200;

        String json = client("backup").generateJson("system", "prompt", Map.of("type", "object"));

        assertThat(json).isEqualTo("{\"ok\":true}");
        assertThat(calledModels).containsExactly("main", "main", "backup");
    }

    @Test
    void skipsMisconfiguredFallbackModel() {
        statusFor = (model, call) -> switch (model) {
            case "main" -> 503;
            case "wrong-name" -> 404;
            default -> 200;
        };

        assertThat(client("wrong-name", "backup").generateJson(grading())).isEqualTo("{\"ok\":true}");
        assertThat(calledModels).endsWith("wrong-name", "backup");
    }

    @Test
    void failsWithClearMessageWhenEveryModelIsBusy() {
        statusFor = (model, call) -> 503;

        assertThatThrownBy(() -> client("backup").generateJson(grading()))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("quá tải")
                .hasMessageContaining("HTTP 503");
        assertThat(calledModels).hasSize(5);
    }

    @Test
    void doesNotRetryPermanentErrors() {
        statusFor = (model, call) -> 404;

        assertThatThrownBy(() -> client("backup").generateJson(grading()))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("HTTP 404");
        assertThat(calledModels).containsExactly("main");
    }

    @Test
    void noRetryAndNoFallbackWhenTurnedOff() {
        statusFor = (model, call) -> 503;
        GeminiProperties properties = new GeminiProperties();
        properties.setApiKey("test-key");
        properties.setModel("main");
        properties.setFallbackModels(List.of());
        properties.setMaxRetries(0);
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());

        assertThatThrownBy(() -> new GeminiLlmClient(properties, RestClient.builder()).generateJson(grading()))
                .isInstanceOf(LlmException.class);
        assertThat(calledModels).containsExactly("main");
    }

    @Test
    void retriesWhenConnectionIsDroppedThenSucceeds() {
        statusFor = (model, call) -> call == 1 ? DROP : 200;

        assertThat(client("backup").generateJson(grading())).isEqualTo("{\"ok\":true}");
        assertThat(calledModels).containsExactly("main", "main");
    }

    @Test
    void failsWithClearMessageWhenConnectionKeepsDropping() {
        statusFor = (model, call) -> DROP;

        assertThatThrownBy(() -> client("backup").generateJson(grading()))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("bị ngắt giữa chừng")
                .hasMessageNotContaining("Error while extracting response");
        // 1 lan dau + 3 lan goi lai model chinh + 1 lan model du phong
        assertThat(calledModels).hasSize(5);
    }

    @Test
    void doesNotRetryWhenGeminiIsTooSlow() {
        statusFor = (model, call) -> SLOW;
        GeminiProperties properties = properties("backup");
        properties.setLongReadTimeoutMs(200);

        assertThatThrownBy(() -> new GeminiLlmClient(properties, RestClient.builder()).generateJson(grading()))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("quá thời gian chờ")
                .hasMessageContaining("GEMINI_LONG_TIMEOUT_MS");
        assertThat(calledModels).containsExactly("main");
    }

    @Test
    void showsWhatCameBackWhenResponseIsNotJson() {
        statusFor = (model, call) -> HTML;

        assertThatThrownBy(() -> client("backup").generateJson(grading()))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("không phải JSON")
                .hasMessageContaining("Blocked by proxy");
        assertThat(calledModels).containsExactly("main");
    }

    /* ========== Model rieng cho viec doc file (GEMINI_IMPORT_MODEL) ========== */

    private GeminiLlmClient clientWithImportModel(String importModel, String... fallbackModels) {
        GeminiProperties properties = properties(fallbackModels);
        properties.setImportModel(importModel);
        return new GeminiLlmClient(properties, RestClient.builder());
    }

    private static LlmRequest documentImport() {
        return LlmRequest.longRunning("system", "prompt", Map.of("type", "object")).forDocumentExtraction();
    }

    @Test
    void documentImportUsesImportModel() {
        assertThat(clientWithImportModel("lite", "backup").generateJson(documentImport())).isEqualTo("{\"ok\":true}");
        assertThat(calledModels).containsExactly("lite");
    }

    @Test
    void otherRequestsIgnoreImportModel() {
        assertThat(clientWithImportModel("lite", "backup").generateJson(grading())).isEqualTo("{\"ok\":true}");
        assertThat(calledModels).containsExactly("main");
    }

    @Test
    void wrongImportModelFallsBackToMainModel() {
        statusFor = (model, call) -> model.equals("lite") ? 404 : 200;

        assertThat(clientWithImportModel("lite", "backup").generateJson(documentImport())).isEqualTo("{\"ok\":true}");
        assertThat(calledModels).containsExactly("lite", "main");
    }

    @Test
    void busyImportModelFallsBackToMainModelAfterRetries() {
        statusFor = (model, call) -> model.equals("lite") ? 503 : 200;

        assertThat(clientWithImportModel("lite", "backup").generateJson(documentImport())).isEqualTo("{\"ok\":true}");
        assertThat(calledModels).containsExactly("lite", "lite", "lite", "lite", "main");
    }

    @Test
    void mainModelErrorStillStopsDocumentImport() {
        statusFor = (model, call) -> model.equals("lite") ? 404 : 403;

        assertThatThrownBy(() -> clientWithImportModel("lite", "backup").generateJson(documentImport()))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("HTTP 403");
        assertThat(calledModels).containsExactly("lite", "main");
    }
}
