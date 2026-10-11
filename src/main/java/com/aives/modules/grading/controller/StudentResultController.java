package com.aives.modules.grading.controller;

import com.aives.common.dto.ApiResponse;
import com.aives.common.security.UserPrincipal;
import com.aives.modules.grading.dto.AttemptReviewResponse;
import com.aives.modules.grading.dto.GradingAttemptResponse;
import com.aives.modules.grading.service.GradingService;
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

@Tag(name = "Student Results", description = "Sinh viên xem lịch sử ca thi và kết quả đã công bố của mình")
@RestController
@RequestMapping("/api/student/attempts")
@PreAuthorize("hasRole('STUDENT')")
@RequiredArgsConstructor
public class StudentResultController {

    private final GradingService gradingService;

    @Operation(summary = "Các lượt thi đã thi xong của mình. Điểm chỉ có khi kết quả đã công bố")
    @GetMapping
    public ApiResponse<List<GradingAttemptResponse>> listMyAttempts(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(gradingService.listMyAttempts(principal.getId()));
    }

    @Operation(summary = "Kết quả chi tiết 1 lượt thi của mình (chỉ khi đã công bố)")
    @GetMapping("/{attemptId}/result")
    public ApiResponse<AttemptReviewResponse> getMyResult(
            @PathVariable String attemptId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(gradingService.getMyResult(principal.getId(), attemptId));
    }
}
