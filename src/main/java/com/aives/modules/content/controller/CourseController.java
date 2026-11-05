package com.aives.modules.content.controller;

import com.aives.common.dto.ApiResponse;
import com.aives.modules.content.dto.CourseResponse;
import com.aives.modules.content.dto.CreateCourseRequest;
import com.aives.modules.content.dto.UpdateCourseRequest;
import com.aives.modules.content.service.CourseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Courses", description = "Quản lý danh mục môn học")
@RestController
@RequestMapping("/api/v1/courses")
@PreAuthorize("hasAnyRole('LECTURER', 'ADMIN')")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @Operation(summary = "Lấy danh sách tất cả môn học")
    @GetMapping
    public ApiResponse<List<CourseResponse>> getAllCourses() {
        return ApiResponse.ok(courseService.getAllCourses());
    }

    @Operation(summary = "Lấy chi tiết môn học theo ID")
    @GetMapping("/{courseId}")
    public ApiResponse<CourseResponse> getCourseById(@PathVariable String courseId) {
        return ApiResponse.ok(courseService.getCourseById(courseId));
    }

    @Operation(summary = "Tạo môn học mới (chỉ Admin)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CourseResponse> createCourse(@Valid @RequestBody CreateCourseRequest request) {
        return ApiResponse.ok("Tạo môn học thành công", courseService.createCourse(request));
    }

    @Operation(summary = "Cập nhật môn học (chỉ Admin)")
    @PutMapping("/{courseId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CourseResponse> updateCourse(
            @PathVariable String courseId,
            @Valid @RequestBody UpdateCourseRequest request) {
        return ApiResponse.ok("Cập nhật môn học thành công", courseService.updateCourse(courseId, request));
    }

    @Operation(summary = "Xoá môn học (chỉ Admin, môn chưa có tài liệu / câu hỏi / phiên thi)")
    @DeleteMapping("/{courseId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> deleteCourse(@PathVariable String courseId) {
        courseService.deleteCourse(courseId);
        return ApiResponse.ok("Xoá môn học thành công", null);
    }
}
