package com.aives.modules.content.controller;

import com.aives.common.dto.ApiResponse;
import com.aives.modules.content.dto.ChapterResponse;
import com.aives.modules.content.dto.CreateChapterRequest;
import com.aives.modules.content.service.ChapterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Chapters", description = "Quản lý các chương thuộc môn học")
@RestController
@RequestMapping("/api/v1/courses")
@PreAuthorize("hasAnyRole('LECTURER', 'ADMIN')")
@RequiredArgsConstructor
public class ChapterController {

    private final ChapterService chapterService;

    @Operation(summary = "Lấy danh sách các chương thuộc môn học")
    @GetMapping("/{courseId}/chapters")
    public ApiResponse<List<ChapterResponse>> getChaptersByCourse(
            @PathVariable String courseId) {
        return ApiResponse.ok(chapterService.getChaptersByCourseId(courseId));
    }

    @Operation(summary = "Tạo chương mới cho môn học")
    @PostMapping("/{courseId}/chapters")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ChapterResponse> createChapter(
            @PathVariable String courseId,
            @Valid @RequestBody CreateChapterRequest request) {
        ChapterResponse response = chapterService.createChapter(courseId, request);
        return ApiResponse.ok("Tạo chương mới thành công", response);
    }
}
