package com.aives.modules.grading.controller;

import com.aives.common.dto.ApiResponse;
import com.aives.common.security.UserPrincipal;
import com.aives.modules.grading.dto.AppealResponse;
import com.aives.modules.grading.dto.SubmitAppealRequest;
import com.aives.modules.grading.service.AppealService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Student Appeals", description = "Sinh viên gửi và xem đơn phúc khảo điểm của mình")
@RestController
@RequestMapping("/api/student/appeals")
@PreAuthorize("hasRole('STUDENT')")
@RequiredArgsConstructor
public class StudentAppealController {

    private final AppealService appealService;

    @Operation(summary = "Gửi đơn phúc khảo cho 1 câu hỏi của lượt thi đã công bố. Mỗi câu chỉ có 1 đơn đang chờ xử lý")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AppealResponse> submit(
            @Valid @RequestBody SubmitAppealRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok("Đã gửi đơn phúc khảo", appealService.submit(
                principal.getId(), request.getQuestionGradeId(), request.getReason()));
    }

    @Operation(summary = "Các đơn phúc khảo mình đã gửi, mới nhất trước")
    @GetMapping
    public ApiResponse<List<AppealResponse>> listMine(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(appealService.listMine(principal.getId()));
    }
}
