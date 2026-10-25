package com.aives.modules.content.controller;

import com.aives.common.dto.ApiResponse;
import com.aives.modules.content.dto.CourseResponse;
import com.aives.modules.content.service.CourseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
