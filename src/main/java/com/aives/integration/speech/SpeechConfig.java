package com.aives.integration.speech;

import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chon SttClient / TtsClient: co GOOGLE_SPEECH_CREDENTIALS (file key ton tai) thi goi Google that,
 * khong co thi dung ban gia (luon bao loi) de app van khoi dong binh thuong.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(SpeechProperties.class)
public class SpeechConfig {

    @Bean
    public SttClient sttClient(SpeechProperties properties) throws IOException {
        CredentialsProvider credentials = loadCredentials(properties);
        if (credentials == null) {
            return new StubSttClient();
        }
        log.info("STT da bat: Google Speech-to-Text, ngon ngu {}", properties.getLanguageCode());
        return new GoogleSttClient(properties, credentials);
    }

    @Bean
    public TtsClient ttsClient(SpeechProperties properties) throws IOException {
        CredentialsProvider credentials = loadCredentials(properties);
        if (credentials == null) {
            return new StubTtsClient();
        }
        log.info("TTS da bat: Google Text-to-Speech, giong {}", properties.getVoiceName());
        return new GoogleTtsClient(properties, credentials);
    }

    /** Doc file JSON key cua service account. Tra ve null neu chua cau hinh hoac khong tim thay file. */
    private CredentialsProvider loadCredentials(SpeechProperties properties) throws IOException {
        String path = properties.getCredentialsPath();
        if (path == null || path.isBlank()) {
            log.warn("[STT/TTS CHUA BAT] Chua co GOOGLE_SPEECH_CREDENTIALS: chuc nang giong noi se bao loi khi goi");
            return null;
        }
        if (Files.notExists(Path.of(path))) {
            log.warn("[STT/TTS CHUA BAT] Khong tim thay file key Google tai: {}", path);
            return null;
        }
        try (InputStream in = new FileInputStream(path)) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(in)
                    .createScoped("https://www.googleapis.com/auth/cloud-platform");
            return FixedCredentialsProvider.create(credentials);
        }
    }
}
