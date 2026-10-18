package com.aives.modules.content.controller;

import com.aives.common.dto.ApiResponse;
import com.aives.common.exception.BusinessException;
import com.aives.common.exception.ErrorCode;
import com.aives.common.security.UserPrincipal;
import com.aives.modules.content.dto.ContentImportResponse;
import com.aives.modules.content.dto.LessonResponse;
import com.aives.modules.content.dto.QuestionFigureResponse;
import com.aives.modules.content.dto.QuestionResponse;
import com.aives.modules.content.dto.QuestionStatusRequest;
import com.aives.modules.content.dto.RubricResponse;
import com.aives.modules.content.dto.TopicResponse;
import com.aives.modules.content.service.ContentImportService;
import com.aives.modules.content.service.ContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Content", description = "Ngân hàng câu hỏi và rubric (LECTURER thấy dữ liệu của mình, ADMIN thấy tất cả)")
@RestController
@RequestMapping("/api/content")
@PreAuthorize("hasAnyRole('LECTURER', 'ADMIN')")
@RequiredArgsConstructor
public class ContentController {

    private final ContentService contentService;
    private final ContentImportService contentImportService;

    @Operation(summary = "Danh sách bài học")
    @GetMapping("/lessons")
    public ApiResponse<List<LessonResponse>> listLessons(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(contentService.listLessons(principal));
    }

    @Operation(summary = "Danh sách chủ đề, lọc theo bài học nếu có lessonId")
    @GetMapping("/topics")
    public ApiResponse<List<TopicResponse>> listTopics(
            @RequestParam(required = false) String lessonId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(contentService.listTopics(principal, lessonId));
    }

    @Operation(summary = "Danh sách rubric kèm các tiêu chí")
    @GetMapping("/rubrics")
    public ApiResponse<List<RubricResponse>> listRubrics(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(contentService.listRubrics(principal));
    }

    @Operation(summary = "Danh sách câu hỏi, lọc theo chủ đề nếu có topicId")
    @GetMapping("/questions")
    public ApiResponse<List<QuestionResponse>> listQuestions(
            @RequestParam(required = false) String topicId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(contentService.listQuestions(principal, topicId));
    }

    @Operation(summary = "Hình vẽ của câu hỏi (cắt từ file PDF lúc nhập), dạng data URL để gắn thẳng vào thẻ img")
    @GetMapping("/questions/{questionId}/figure")
    public ApiResponse<QuestionFigureResponse> getQuestionFigure(
            @PathVariable String questionId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(contentService.getQuestionFigure(principal, questionId));
    }

    @Operation(summary = "Đổi trạng thái câu hỏi: APPROVED (duyệt, phải có rubric), REJECTED, ARCHIVED, DRAFT")
    @PutMapping("/questions/{questionId}/status")
    public ApiResponse<QuestionResponse> changeQuestionStatus(
            @PathVariable String questionId,
            @Valid @RequestBody QuestionStatusRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(contentService.changeQuestionStatus(
                principal, questionId, request.getStatus(), request.getRubricId()));
    }

    @Operation(summary = "Gửi file câu hỏi + đáp án (.pdf, .docx, .txt). AI tách thành câu hỏi NHÁP để giảng viên duyệt. rubricId: rubric gắn cho cả bộ câu hỏi (tuỳ chọn)")
    @PostMapping(value = "/imports/questions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ContentImportResponse> importQuestions(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "rubricId", required = false) String rubricId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        ContentImportResponse result = contentImportService.importQuestions(
                principal, file.getOriginalFilename(), bytesOf(file), rubricId);
        return ApiResponse.ok("Đã tách được " + result.getQuestions().size() + " câu hỏi nháp từ file", result);
    }

    @Operation(summary = "Gửi file tiêu chí chấm điểm (.pdf, .docx, .txt). AI tách thành 1 hoặc nhiều rubric")
    @PostMapping(value = "/imports/rubrics", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ContentImportResponse> importRubrics(
            @RequestParam("file") MultipartFile file,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        ContentImportResponse result = contentImportService.importRubrics(principal, file.getOriginalFilename(), bytesOf(file));
        return ApiResponse.ok("Đã tách được " + result.getRubrics().size() + " rubric từ file", result);
    }

    private byte[] bytesOf(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.IMPORT_FILE_INVALID, "Không đọc được file gửi lên");
        }
    }
}
