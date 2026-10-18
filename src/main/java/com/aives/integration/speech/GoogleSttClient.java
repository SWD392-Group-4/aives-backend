package com.aives.integration.speech;

import com.aives.common.exception.ErrorCode;
import com.google.api.gax.core.CredentialsProvider;
import com.google.cloud.speech.v1.RecognitionAudio;
import com.google.cloud.speech.v1.RecognitionConfig;
import com.google.cloud.speech.v1.RecognizeResponse;
import com.google.cloud.speech.v1.SpeechClient;
import com.google.cloud.speech.v1.SpeechRecognitionResult;
import com.google.cloud.speech.v1.SpeechSettings;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/** Goi Google Cloud Speech-to-Text (v1, recognize dong bo: audio toi da ~60 giay). */
@Slf4j
public class GoogleSttClient implements SttClient, AutoCloseable {

    private final SpeechClient speechClient;
    private final SpeechProperties properties;

    public GoogleSttClient(SpeechProperties properties, CredentialsProvider credentialsProvider) throws IOException {
        this.properties = properties;
        this.speechClient = SpeechClient.create(SpeechSettings.newBuilder()
                .setCredentialsProvider(credentialsProvider)
                .build());
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String transcribe(byte[] audioBytes) {
        if (audioBytes == null || audioBytes.length == 0) {
            throw new SpeechException(ErrorCode.BAD_REQUEST, "Audio rỗng, không thể chuyển thành văn bản");
        }

        RecognitionConfig config = RecognitionConfig.newBuilder()
                .setEncoding(RecognitionConfig.AudioEncoding.WEBM_OPUS)
                .setSampleRateHertz(properties.getSampleRateHertz())
                .setLanguageCode(properties.getLanguageCode())
                .setEnableAutomaticPunctuation(true)
                .build();

        RecognitionAudio audio = RecognitionAudio.newBuilder()
                .setContent(ByteString.copyFrom(audioBytes))
                .build();

        try {
            RecognizeResponse response = speechClient.recognize(config, audio);
            return response.getResultsList().stream()
                    .map(this::bestTranscript)
                    .collect(Collectors.joining(" "))
                    .trim();
        } catch (Exception ex) {
            log.warn("Goi Google STT that bai: {}", ex.getMessage());
            throw new SpeechException(ErrorCode.SPEECH_REQUEST_FAILED,
                    "Không chuyển được giọng nói thành văn bản: " + ex.getMessage(), ex);
        }
    }

    private String bestTranscript(SpeechRecognitionResult result) {
        return result.getAlternativesCount() > 0 ? result.getAlternatives(0).getTranscript() : "";
    }

    @Override
    public void close() {
        speechClient.close();
    }
}
