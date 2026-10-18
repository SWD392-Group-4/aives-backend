package com.aives.integration.speech;

import com.aives.common.exception.ErrorCode;
import com.google.api.gax.core.CredentialsProvider;
import com.google.cloud.texttospeech.v1.AudioConfig;
import com.google.cloud.texttospeech.v1.AudioEncoding;
import com.google.cloud.texttospeech.v1.SynthesisInput;
import com.google.cloud.texttospeech.v1.SynthesizeSpeechResponse;
import com.google.cloud.texttospeech.v1.TextToSpeechClient;
import com.google.cloud.texttospeech.v1.TextToSpeechSettings;
import com.google.cloud.texttospeech.v1.VoiceSelectionParams;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

/** Goi Google Cloud Text-to-Speech, tra ve audio MP3. */
@Slf4j
public class GoogleTtsClient implements TtsClient, AutoCloseable {

    private final TextToSpeechClient textToSpeechClient;
    private final SpeechProperties properties;

    public GoogleTtsClient(SpeechProperties properties, CredentialsProvider credentialsProvider) throws IOException {
        this.properties = properties;
        this.textToSpeechClient = TextToSpeechClient.create(TextToSpeechSettings.newBuilder()
                .setCredentialsProvider(credentialsProvider)
                .build());
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public byte[] synthesize(String text) {
        if (text == null || text.isBlank()) {
            throw new SpeechException(ErrorCode.BAD_REQUEST, "Văn bản rỗng, không thể chuyển thành giọng nói");
        }

        SynthesisInput input = SynthesisInput.newBuilder()
                .setText(text)
                .build();

        VoiceSelectionParams voice = VoiceSelectionParams.newBuilder()
                .setLanguageCode(properties.getLanguageCode())
                .setName(properties.getVoiceName())
                .build();

        AudioConfig audioConfig = AudioConfig.newBuilder()
                .setAudioEncoding(AudioEncoding.MP3)
                .setSpeakingRate(properties.getSpeakingRate())
                .build();

        try {
            SynthesizeSpeechResponse response = textToSpeechClient.synthesizeSpeech(input, voice, audioConfig);
            return response.getAudioContent().toByteArray();
        } catch (Exception ex) {
            log.warn("Goi Google TTS that bai: {}", ex.getMessage());
            throw new SpeechException(ErrorCode.SPEECH_REQUEST_FAILED,
                    "Không chuyển được văn bản thành giọng nói: " + ex.getMessage(), ex);
        }
    }

    @Override
    public void close() {
        textToSpeechClient.close();
    }
}
