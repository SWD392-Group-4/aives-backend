package com.aives.modules.content.controller;

import com.aives.common.dto.ApiResponse;
import com.aives.common.security.UserPrincipal;
import com.aives.modules.content.dto.CourseRubricResponse;
import com.aives.modules.content.dto.UpdateCourseRubricRequest;
import com.aives.modules.content.service.CourseRubricService;
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

@Tag(name = "Course Rubric", description = "Quản lý Rubric chuẩn duy nhất của môn học (BR-BANK-001, BR-BANK-006)")
@RestController
@RequestMapping("/api/v1/courses")
@PreAuthorize("hasAnyRole('LECTURER', 'ADMIN')")
@RequiredArgsConstructor
public class CourseRubricController {

    private final CourseRubricService courseRubricService;

    @Operation(summary = "Lấy Rubric chuẩn duy nhất của môn học")
    @GetMapping("/{courseId}/rubric")
    public ApiResponse<CourseRubricResponse> getCourseRubric(
            @PathVariable String courseId) {
        return ApiResponse.ok(courseRubricService.getRubricByCourseId(courseId));
    }

    @Operation(summary = "Thiết lập hoặc cập nhật Rubric duy nhất của môn học")
    @PutMapping("/{courseId}/rubric")
    public ApiResponse<CourseRubricResponse> updateCourseRubric(
            @PathVariable String courseId,
            @Valid @RequestBody UpdateCourseRubricRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        CourseRubricResponse response = courseRubricService.updateRubric(courseId, request, principal);
        return ApiResponse.ok("Cập nhật Rubric chuẩn của môn học thành công", response);
    }
}
