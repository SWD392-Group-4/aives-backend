package com.aives.modules.exam.controller;

import com.aives.common.dto.ApiResponse;
import com.aives.common.dto.PageResponse;
import com.aives.common.security.UserPrincipal;
import com.aives.modules.exam.dto.DeleteExamSessionResponse;
import com.aives.modules.exam.dto.ExamSessionRequest;
import com.aives.modules.exam.dto.ExamSessionResponse;
import com.aives.modules.exam.service.ExamSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Exam Sessions", description = "Quản lý phiên thi (LECTURER: phiên của mình, ADMIN: mọi phiên)")
@RestController
@RequestMapping("/api/exam-sessions")
@PreAuthorize("hasAnyRole('LECTURER', 'ADMIN')")
@RequiredArgsConstructor
public class ExamSessionController {

    private final ExamSessionService examSessionService;

    @Operation(summary = "Tạo phiên thi. Trả về id phiên (AIVES_EXAM_yyyy_xxxxxx, cũng là mã vào thi) và mã truy cập 6 số")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ExamSessionResponse> createSession(
            @Valid @RequestBody ExamSessionRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok("Tạo phiên thi thành công", examSessionService.createSession(request, principal));
    }

    @Operation(summary = "Danh sách phiên thi, có phân trang. status: UPCOMING | ONGOING | ENDED | CANCELLED")
    @GetMapping
    public ApiResponse<PageResponse<ExamSessionResponse>> getSessions(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(examSessionService.getSessions(principal, status, keyword, page, size));
    }

    @Operation(summary = "Chi tiết 1 phiên thi theo id (vd: AIVES_EXAM_2026_049282)")
    @GetMapping("/{id}")
    public ApiResponse<ExamSessionResponse> getSession(
            @PathVariable String id,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(examSessionService.getSession(id, principal));
    }

    @Operation(summary = "Sửa phiên thi. Phiên đã mở chỉ sửa được tên, mô tả, kéo dài giờ đóng và tăng số lượt thi tối đa")
    @PutMapping("/{id}")
    public ApiResponse<ExamSessionResponse> updateSession(
            @PathVariable String id,
            @Valid @RequestBody ExamSessionRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok("Cập nhật phiên thi thành công", examSessionService.updateSession(id, request, principal));
    }

    @Operation(summary = "Xoá phiên thi. Đã có sinh viên vào thì chỉ huỷ phiên (action = CANCELLED), dữ liệu được giữ lại")
    @DeleteMapping("/{id}")
    public ApiResponse<DeleteExamSessionResponse> deleteSession(
            @PathVariable String id,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        DeleteExamSessionResponse result = examSessionService.deleteSession(id, principal);
        String message = DeleteExamSessionResponse.DELETED.equals(result.getAction())
                ? "Xoá phiên thi thành công"
                : "Phiên thi đã có sinh viên tham gia nên được chuyển sang trạng thái đã huỷ";
        return ApiResponse.ok(message, result);
    }

    @Operation(summary = "Tạo lại mã truy cập 6 số. Mã cũ hết hiệu lực ngay")
    @PostMapping("/{id}/regenerate-passcode")
    public ApiResponse<ExamSessionResponse> regeneratePasscode(
            @PathVariable String id,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok("Đã tạo mã truy cập mới", examSessionService.regeneratePasscode(id, principal));
    }
}
