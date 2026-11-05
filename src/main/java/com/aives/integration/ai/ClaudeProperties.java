package com.aives.integration.ai;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Cau hinh Claude (Anthropic), doc tu app.ai.claude.* trong application.yml (gia tri lay tu file .env). */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.ai.claude")
public class ClaudeProperties {

    /** Key tao o console.anthropic.com. De trong = tat AI. */
    private String apiKey = "";

    private String model = "claude-haiku-5-5";

    private String baseUrl = "https://api.anthropic.com";

    /** Phien ban API cua Anthropic, gui trong header anthropic-version. */
    private String apiVersion = "2023-06-01";

    /** So token toi da model duoc tra loi cho viec nang (cham diem, doc file, sinh nhieu cau hoi). */
    private int maxTokens = 8192;

    /** So token toi da cho viec can nhanh (hoi xoay luc dang thi). */
    private int quickMaxTokens = 2048;

    /** So lan goi lai khi loi tam thoi (429 / 5xx / 529), cho viec nang. 0 = khong goi lai. */
    private int maxRetries = 3;

    /** So lan goi lai khi loi tam thoi cho hoi xoay luc dang thi (can nhanh nen de it). */
    private int quickMaxRetries = 1;

    /** Cho bao lau truoc lan goi lai dau tien (ms); cac lan sau gap doi, toi da 10 giay. */
    private long retryDelayMs = 2000;

    private int connectTimeoutMs = 5000;

    /** Thoi gian toi da cho 1 lan goi luc dang thi. Qua thoi gian nay coi nhu AI loi. */
    private int readTimeoutMs = 20000;

    /** Thoi gian toi da cho viec nang: cham diem, doc file, sinh cau hoi. */
    private int longReadTimeoutMs = 120000;
}
