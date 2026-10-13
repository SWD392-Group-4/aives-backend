package com.aives.integration.ai;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Cau hinh Gemini, doc tu app.ai.gemini.* trong application.yml (gia tri lay tu file .env). */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.ai.gemini")
public class GeminiProperties {

    /** Key tao o Google AI Studio. De trong = tat AI. */
    private String apiKey = "";

    private String model = "gemini-3.8-flash";

    /**
     * Model du phong, thu lan luot khi model chinh loi tam thoi (429 / 5xx) du da goi lai.
     * De rong = khong dung model du phong. Ten sai thi model do bi bo qua (co ghi log).
     */
    private List<String> fallbackModels = List.of("gemini-3.6-flash", "gemini-3.5-flash-lite");

    /**
     * Model rieng cho viec doc file cau hoi / rubric giang vien gui len (chi tach du lieu, khong can suy luan sau).
     * Flash-Lite tra loi nhanh hon ro ret so voi Flash. De trong = dung model chinh.
     * Model nay qua tai / sai ten thi tu chuyen sang model chinh roi toi cac model du phong.
     */
    private String importModel = "";

    /** So lan goi lai model chinh khi loi tam thoi, cho viec nang (cham diem, doc file). 0 = khong goi lai. */
    private int maxRetries = 3;

    /** So lan goi lai model chinh khi loi tam thoi, cho hoi xoay luc dang thi (can nhanh nen de it). */
    private int quickMaxRetries = 1;

    /** Cho bao lau truoc lan goi lai dau tien (ms); cac lan sau gap doi, toi da 10 giay. */
    private long retryDelayMs = 2000;

    private String baseUrl = "https://generativelanguage.googleapis.com/v1beta";

    private int connectTimeoutMs = 5000;

    /** Thoi gian toi da cho 1 lan goi. Qua thoi gian nay coi nhu AI loi. */
    private int readTimeoutMs = 20000;

    /** Thoi gian toi da cho viec nang: cham diem 1 cau hoi, doc file cau hoi giang vien gui len. */
    private int longReadTimeoutMs = 120000;

    /**
     * Do "sang tao" cua model. So am = khong gui, de model dung mac dinh
     * (Google khuyen giu mac dinh 1.0 voi dong Gemini 3, dat thap de bi lap hoac giam chat luong).
     */
    private double temperature = -1;

    /**
     * Muc "suy nghi" cua dong Gemini 3: low / medium / high (gemini-3.8-flash khong nhan minimal). "low" de tra loi nhanh.
     * De trong = khong gui (model dung mac dinh). Neu model tu choi tham so nay (HTTP 400),
     * he thong tu goi lai khong kem tham so va khong gui nua cho toi khi khoi dong lai.
     */
    private String thinkingLevel = "low";

    /**
     * Ngan sach "suy nghi" kieu cu cua dong Gemini 2.5 (0 = tat). Chi duoc gui khi thinkingLevel de trong
     * va gia tri >= 0, vi Gemini bao loi neu gui ca hai.
     */
    private int thinkingBudget = -1;
}
