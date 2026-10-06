package com.aives.modules.vivaroom.controller;

import com.aives.common.dto.ApiResponse;
import com.aives.common.security.UserPrincipal;
import com.aives.modules.vivaroom.dto.InterviewExchangeResponse;
import com.aives.modules.vivaroom.service.VivaInterviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Viva Interview Review", description = "Giảng viên / admin xem lại nội dung hỏi - đáp của 1 lượt thi")
@RestController
@RequestMapping("/api/exam-sessions/attempts/{attemptId}/interview")
@PreAuthorize("hasAnyRole('LECTURER', 'ADMIN')")
@RequiredArgsConstructor
public class InterviewReviewController {

    private final VivaInterviewService interviewService;

    @Operation(summary = "Toàn bộ lượt hỏi - đáp của 1 lượt thi, kèm quyết định và lý do của AI (người tạo phiên hoặc admin)")
    @GetMapping("/exchanges")
    public ApiResponse<List<InterviewExchangeResponse>> listExchanges(
            @PathVariable String attemptId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(interviewService.listExchanges(attemptId, principal));
    }
}
