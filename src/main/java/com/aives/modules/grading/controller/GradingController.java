package com.aives.modules.grading.controller;

import com.aives.common.dto.ApiResponse;
import com.aives.common.security.UserPrincipal;
import com.aives.modules.grading.dto.AppealResponse;
import com.aives.modules.grading.dto.AttemptReviewResponse;
import com.aives.modules.grading.dto.GradingAttemptResponse;
import com.aives.modules.grading.dto.QuestionGradeResponse;
import com.aives.modules.grading.dto.ResolveAppealRequest;
import com.aives.modules.grading.dto.SaveQuestionGradeRequest;
import com.aives.modules.grading.service.AppealService;
import com.aives.modules.grading.service.GradingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Grading", description = "Giảng viên / admin thẩm định điểm AI chấm và công bố kết quả (HITL)")
@RestController
@RequestMapping("/api/grading")
@PreAuthorize("hasAnyRole('LECTURER', 'ADMIN')")
@RequiredArgsConstructor
public class GradingController {

    private final GradingService gradingService;
    private final AppealService appealService;

    @Operation(summary = "Các lượt thi đã thi xong của những phiên thi do mình tạo (admin: mọi phiên)")
    @GetMapping("/attempts")
    public ApiResponse<List<GradingAttemptResponse>> listAttempts(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(gradingService.listAttemptsForReview(principal));
    }

    @Operation(summary = "Chi tiết 1 lượt thi để thẩm định: điểm từng câu, từng tiêu chí và nội dung hỏi - đáp")
    @GetMapping("/attempts/{attemptId}")
    public ApiResponse<AttemptReviewResponse> getReview(
            @PathVariable String attemptId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(gradingService.getReview(principal, attemptId));
    }

    @Operation(summary = "Chốt điểm 1 câu. Lệch quá 2 điểm so với điểm AI thì bắt buộc có ghi chú")
    @PutMapping("/question-grades/{questionGradeId}")
    public ApiResponse<QuestionGradeResponse> saveQuestionGrade(
            @PathVariable String questionGradeId,
            @Valid @RequestBody SaveQuestionGradeRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok("Đã chốt điểm câu hỏi", gradingService.saveQuestionGrade(
                principal, questionGradeId, request));
    }

    @Operation(summary = "Công bố kết quả cho sinh viên. Mọi câu phải được chốt điểm trước")
    @PostMapping("/attempts/{attemptId}/publish")
    public ApiResponse<GradingAttemptResponse> publish(
            @PathVariable String attemptId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok("Đã công bố kết quả", gradingService.publish(principal, attemptId));
    }

    @Operation(summary = "Cho AI chấm lại các câu giảng viên chưa chốt điểm (vd sau khi AI chấm lỗi). AI chấm ngầm, vài giây sau tải lại để xem")
    @PostMapping("/attempts/{attemptId}/regrade")
    public ApiResponse<GradingAttemptResponse> regrade(
            @PathVariable String attemptId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok("Đã yêu cầu AI chấm lại", gradingService.requestRegrade(principal, attemptId));
    }

    @Operation(summary = "Đơn phúc khảo của các phiên thi do mình tạo (admin: mọi phiên), mới nhất trước")
    @GetMapping("/appeals")
    public ApiResponse<List<AppealResponse>> listAppeals(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(appealService.listForReview(principal));
    }

    @Operation(summary = "Xử lý đơn phúc khảo. ACCEPTED: đổi điểm chốt của câu hỏi thành scoreAfter và tính lại tổng điểm. REJECTED: giữ nguyên điểm")
    @PutMapping("/appeals/{appealId}")
    public ApiResponse<AppealResponse> resolveAppeal(
            @PathVariable String appealId,
            @Valid @RequestBody ResolveAppealRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok("Đã xử lý đơn phúc khảo", appealService.resolve(
                principal, appealId, request.getStatus(), request.getScoreAfter(), request.getResponse()));
    }
}
