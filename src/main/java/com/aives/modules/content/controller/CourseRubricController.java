package com.aives.modules.content.controller;

import com.aives.common.dto.ApiResponse;
import com.aives.common.security.UserPrincipal;
import com.aives.modules.content.dto.CourseRubricResponse;
import com.aives.modules.content.dto.UpdateCourseRubricRequest;
import com.aives.modules.content.service.ICourseRubricService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Course Rubric", description = "Quản lý Rubric chuẩn duy nhất của môn học")
@RestController
@RequestMapping("/api/v1/courses/{courseId}/rubric")
@RequiredArgsConstructor
public class CourseRubricController {

    private final ICourseRubricService courseRubricService;

    @Operation(summary = "Lấy Rubric chuẩn duy nhất của môn học", description = "Trả về bộ Rubric duy nhất của môn học kèm danh sách tiêu chí và trạng thái khóa.")
    @GetMapping
    public ApiResponse<CourseRubricResponse> getCourseRubric(
            @Parameter(description = "Mã định danh môn học") @PathVariable String courseId) {
        CourseRubricResponse response = courseRubricService.getRubricByCourseId(courseId);
        return ApiResponse.ok("Lấy thông tin Rubric thành công", response);
    }

    @Operation(summary = "Thiết lập hoặc cập nhật Rubric duy nhất của môn học",
            description = "Giảng viên cập nhật các tiêu chí Rubric của môn học. Tổng weight bắt buộc = 100% (BR-BANK-001) và chặn nếu đã bị khóa (BR-BANK-006).")
    @PreAuthorize("hasAnyRole('LECTURER', 'ADMIN')")
    @PutMapping
    public ApiResponse<CourseRubricResponse> updateCourseRubric(
            @Parameter(description = "Mã định danh môn học") @PathVariable String courseId,
            @Valid @RequestBody UpdateCourseRubricRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        CourseRubricResponse response = courseRubricService.updateRubric(courseId, request, principal.getId());
        return ApiResponse.ok("Cập nhật Rubric thành công", response);
    }
}