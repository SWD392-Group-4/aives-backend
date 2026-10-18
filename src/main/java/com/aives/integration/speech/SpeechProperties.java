package com.aives.integration.speech;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Cau hinh Google Speech-to-Text / Text-to-Speech, doc tu app.speech.* trong application.yml (gia tri lay tu file .env). */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.speech")
public class SpeechProperties {

    /** Duong dan file JSON key cua service account Google Cloud. De trong = tat STT / TTS (dung ban gia). */
    private String credentialsPath = "";

    /** Ngon ngu nhan dang va doc: vi-VN hoac en-US. */
    private String languageCode = "vi-VN";

    /** Tan so mau cua audio webm/opus ghi tu trinh duyet (MediaRecorder) thuong la 48000. */
    private int sampleRateHertz = 48000;

    /** Giong doc TTS, vd vi-VN-Standard-A (re nhat), vi-VN-Wavenet-A (tu nhien hon). */
    private String voiceName = "vi-VN-Standard-A";

    /** Toc do doc: 0.25 - 4.0, 1.0 = binh thuong. */
    private double speakingRate = 1.0;
}
