package com.aives.common.controller;

import com.aives.common.dto.ApiResponse;
import com.aives.integration.speech.SttClient;
import com.aives.integration.speech.TtsClient;
import java.io.IOException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** API thu STT / TTS (can dang nhap, gui kem JWT). Test qua Swagger /swagger-ui.html hoac Postman. */
@RestController
@RequestMapping("/api/speech")
@RequiredArgsConstructor
public class SpeechController {

    private final SttClient sttClient;
    private final TtsClient ttsClient;

    /** Gui file audio (webm/opus, <= 60 giay), tra ve van ban. */
    @PostMapping(value = "/stt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, String>> speechToText(@RequestParam("file") MultipartFile file) throws IOException {
        String transcript = sttClient.transcribe(file.getBytes());
        return ApiResponse.ok(Map.of("transcript", transcript));
    }

    /** Gui {"text": "..."}, tra ve file MP3. */
    @PostMapping(value = "/tts", produces = "audio/mpeg")
    public ResponseEntity<byte[]> textToSpeech(@RequestBody Map<String, String> body) {
        byte[] audio = ttsClient.synthesize(body.get("text"));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("audio/mpeg")).body(audio);
    }
}
