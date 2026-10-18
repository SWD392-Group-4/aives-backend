package com.aives.modules.exam.controller;

import com.aives.common.dto.ApiResponse;
import com.aives.common.security.UserPrincipal;
import com.aives.modules.exam.dto.ExamAttemptResponse;
import com.aives.modules.exam.dto.JoinExamRequest;
import com.aives.modules.exam.service.ExamAttemptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Exam Attempts", description = "Sinh viên vào phiên thi và xem lượt thi của mình (chỉ STUDENT)")
@RestController
@RequestMapping("/api/exam-sessions")
@PreAuthorize("hasRole('STUDENT')")
@RequiredArgsConstructor
public class ExamAttemptController {

    private final ExamAttemptService examAttemptService;

    @Operation(summary = "Vào phiên thi bằng mã phiên + mã truy cập. Đang có lượt thi chưa xong thì nhận lại đúng lượt đó; đã xong và còn lượt thì tạo lượt thi mới")
    @PostMapping("/join")
    public ApiResponse<ExamAttemptResponse> joinSession(
            @Valid @RequestBody JoinExamRequest request,
            @RequestHeader(value = "Platform", required = false, defaultValue = "WEB") String platform,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        String effectivePlatform = StringUtils.hasText(request.getPlatform()) ? request.getPlatform() : platform;
        return ApiResponse.ok("Vào phiên thi thành công", examAttemptService.joinSession(principal.getId(), request, effectivePlatform));
    }

    @Operation(summary = "Xem lượt thi của mình: hạn làm bài (deadlineAt) và giờ server để đếm ngược")
    @GetMapping("/attempts/{attemptId}")
    public ApiResponse<ExamAttemptResponse> getAttempt(
            @PathVariable String attemptId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(examAttemptService.getAttempt(attemptId, principal.getId()));
    }

    @Operation(summary = "Kết thúc bài thi trước khi hết giờ. Muốn thi lại phải còn lượt thi (maxAttempts của phiên)")
    @PostMapping("/attempts/{attemptId}/finish")
    public ApiResponse<ExamAttemptResponse> finishAttempt(
            @PathVariable String attemptId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok("Đã kết thúc bài thi", examAttemptService.finishAttempt(attemptId, principal.getId()));
    }
}
