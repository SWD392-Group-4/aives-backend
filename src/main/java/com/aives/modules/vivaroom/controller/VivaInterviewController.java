package com.aives.modules.vivaroom.controller;

import com.aives.common.dto.ApiResponse;
import com.aives.common.security.UserPrincipal;
import com.aives.modules.vivaroom.dto.InterviewStateResponse;
import com.aives.modules.vivaroom.dto.SkipQuestionRequest;
import com.aives.modules.vivaroom.dto.SubmitAnswerRequest;
import com.aives.modules.vivaroom.service.VivaInterviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Viva Interview", description = "Phỏng vấn AI trong 1 lượt thi: nhận câu hỏi, trả lời, hỏi xoáy (chỉ STUDENT)")
@RestController
@RequestMapping("/api/exam-sessions/attempts/{attemptId}/interview")
@PreAuthorize("hasRole('STUDENT')")
@RequiredArgsConstructor
public class VivaInterviewController {

    private final VivaInterviewService interviewService;

    @Operation(summary = "Bắt đầu phỏng vấn, hoặc lấy lại câu hỏi đang dở khi tải lại trang. Gọi lại nhiều lần không tạo câu hỏi mới")
    @PostMapping("/start")
    public ApiResponse<InterviewStateResponse> start(
            @PathVariable String attemptId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(interviewService.start(attemptId, principal.getId()));
    }

    @Operation(summary = "Nộp câu trả lời (chữ) cho câu hỏi hiện tại. AI quyết định hỏi xoáy hay chuyển câu; trả về câu hỏi kế tiếp hoặc COMPLETED")
    @PostMapping("/answer")
    public ApiResponse<InterviewStateResponse> answer(
            @PathVariable String attemptId,
            @Valid @RequestBody SubmitAnswerRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(interviewService.answer(
                attemptId, principal.getId(), request.getExchangeId(), request.getTranscript()));
    }

    @Operation(summary = "Bỏ qua câu hỏi hiện tại, chuyển sang câu hỏi gốc tiếp theo")
    @PostMapping("/skip")
    public ApiResponse<InterviewStateResponse> skip(
            @PathVariable String attemptId,
            @Valid @RequestBody SkipQuestionRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(interviewService.skip(attemptId, principal.getId(), request.getExchangeId()));
    }
}
