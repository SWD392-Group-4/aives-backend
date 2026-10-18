package com.aives.modules.vivaroom.controller;

import com.aives.common.dto.ApiResponse;
import com.aives.common.security.UserPrincipal;
import com.aives.integration.speech.SttClient;
import com.aives.integration.speech.TtsClient;
import com.aives.modules.vivaroom.dto.InterviewStateResponse;
import com.aives.modules.vivaroom.dto.SkipQuestionRequest;
import com.aives.modules.vivaroom.dto.SubmitAnswerRequest;
import com.aives.modules.vivaroom.service.VivaInterviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Viva Interview", description = "Phỏng vấn AI trong 1 lượt thi: nhận câu hỏi, trả lời, hỏi xoáy (chỉ STUDENT)")
@RestController
@RequestMapping("/api/exam-sessions/attempts/{attemptId}/interview")
@PreAuthorize("hasRole('STUDENT')")
@RequiredArgsConstructor
public class VivaInterviewController {

    private final VivaInterviewService interviewService;
    private final SttClient sttClient;
    private final TtsClient ttsClient;

    @Operation(summary = "Bắt đầu phỏng vấn, hoặc lấy lại câu hỏi đang dở khi tải lại trang. Gọi lại nhiều lần không tạo câu hỏi mới")
    @PostMapping("/start")
    public ApiResponse<InterviewStateResponse> start(
            @PathVariable String attemptId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(withSpeechFlags(interviewService.start(attemptId, principal.getId())));
    }

    @Operation(summary = "Nộp câu trả lời (chữ) cho câu hỏi hiện tại. AI quyết định hỏi xoáy hay chuyển câu; trả về câu hỏi kế tiếp hoặc COMPLETED")
    @PostMapping("/answer")
    public ApiResponse<InterviewStateResponse> answer(
            @PathVariable String attemptId,
            @Valid @RequestBody SubmitAnswerRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(withSpeechFlags(interviewService.answer(
                attemptId, principal.getId(), request.getExchangeId(), request.getTranscript())));
    }

    @Operation(summary = "Nộp câu trả lời bằng giọng nói (file webm/opus, tối đa ~60 giây). Backend chuyển thành chữ (STT) rồi xử lý như /answer")
    @PostMapping(value = "/answer-audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<InterviewStateResponse> answerAudio(
            @PathVariable String attemptId,
            @RequestParam("exchangeId") String exchangeId,
            @RequestParam("file") MultipartFile file,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) throws IOException {
        // Kiem tra dung cau dang cho tra loi TRUOC khi goi Google, tranh ton luot STT cho request sai
        interviewService.currentQuestionText(attemptId, principal.getId(), exchangeId);
        String transcript = sttClient.transcribe(file.getBytes());
        return ApiResponse.ok(withSpeechFlags(interviewService.answer(
                attemptId, principal.getId(), exchangeId, transcript)));
    }

    @Operation(summary = "Đọc câu hỏi hiện tại thành giọng nói (TTS), trả về file MP3")
    @GetMapping(value = "/question-audio", produces = "audio/mpeg")
    public ResponseEntity<byte[]> questionAudio(
            @PathVariable String attemptId,
            @RequestParam("exchangeId") String exchangeId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        String questionText = interviewService.currentQuestionText(attemptId, principal.getId(), exchangeId);
        byte[] audio = ttsClient.synthesize(questionText);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/mpeg"))
                .cacheControl(CacheControl.noStore())
                .body(audio);
    }

    @Operation(summary = "Bỏ qua câu hỏi hiện tại, chuyển sang câu hỏi gốc tiếp theo")
    @PostMapping("/skip")
    public ApiResponse<InterviewStateResponse> skip(
            @PathVariable String attemptId,
            @Valid @RequestBody SkipQuestionRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(withSpeechFlags(interviewService.skip(attemptId, principal.getId(), request.getExchangeId())));
    }

    /** Bao cho FE biet backend co bat STT / TTS khong, de chon dung cach ghi am / doc cau hoi. */
    private InterviewStateResponse withSpeechFlags(InterviewStateResponse state) {
        if (state == null) {
            return null;
        }
        return state.toBuilder()
                .sttEnabled(sttClient.isEnabled())
                .ttsEnabled(ttsClient.isEnabled())
                .build();
    }
}
