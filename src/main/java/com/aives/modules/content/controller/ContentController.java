package com.aives.modules.content.controller;

import com.aives.common.dto.ApiResponse;
import com.aives.common.exception.BusinessException;
import com.aives.common.exception.ErrorCode;
import com.aives.common.security.UserPrincipal;
import com.aives.modules.content.dto.ContentImportResponse;
import com.aives.modules.content.dto.CreateLessonRequest;
import com.aives.modules.content.dto.CreateQuestionRequest;
import com.aives.modules.content.dto.CreateRubricRequest;
import com.aives.modules.content.dto.CreateTopicRequest;
import com.aives.modules.content.dto.LessonResponse;
import com.aives.modules.content.dto.QuestionFigureResponse;
import com.aives.modules.content.dto.QuestionResponse;
import com.aives.modules.content.dto.QuestionStatusRequest;
import com.aives.modules.content.dto.RubricResponse;
import com.aives.modules.content.dto.TopicResponse;
import com.aives.modules.content.dto.UpdateLessonRequest;
import com.aives.modules.content.dto.UpdateQuestionRequest;
import com.aives.modules.content.dto.UpdateRubricRequest;
import com.aives.modules.content.dto.UpdateTopicRequest;
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

    @Operation(summary = "Tạo bài học mới")
    @PostMapping("/lessons")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<LessonResponse> createLesson(
            @Valid @RequestBody CreateLessonRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok("Tạo bài học thành công", contentService.createLesson(principal, request));
    }

    @Operation(summary = "Cập nhật bài học")
    @PutMapping("/lessons/{id}")
    public ApiResponse<LessonResponse> updateLesson(
            @PathVariable String id,
            @Valid @RequestBody UpdateLessonRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok("Cập nhật bài học thành công", contentService.updateLesson(principal, id, request));
    }

    @Operation(summary = "Xoá bài học (chỉ xoá được khi chưa có chủ đề)")
    @DeleteMapping("/lessons/{id}")
    public ApiResponse<Void> deleteLesson(
            @PathVariable String id,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        contentService.deleteLesson(principal, id);
        return ApiResponse.ok("Xoá bài học thành công", null);
    }

    @Operation(summary = "Danh sách chủ đề, lọc theo bài học nếu có lessonId")
    @GetMapping("/topics")
    public ApiResponse<List<TopicResponse>> listTopics(
            @RequestParam(required = false) String lessonId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(contentService.listTopics(principal, lessonId));
    }

    @Operation(summary = "Tạo chủ đề mới thuộc bài học")
    @PostMapping("/topics")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TopicResponse> createTopic(
            @Valid @RequestBody CreateTopicRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok("Tạo chủ đề thành công", contentService.createTopic(principal, request));
    }

    @Operation(summary = "Cập nhật chủ đề")
    @PutMapping("/topics/{id}")
    public ApiResponse<TopicResponse> updateTopic(
            @PathVariable String id,
            @Valid @RequestBody UpdateTopicRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok("Cập nhật chủ đề thành công", contentService.updateTopic(principal, id, request));
    }

    @Operation(summary = "Xoá chủ đề (chỉ xoá được khi chưa có câu hỏi)")
    @DeleteMapping("/topics/{id}")
    public ApiResponse<Void> deleteTopic(
            @PathVariable String id,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        contentService.deleteTopic(principal, id);
        return ApiResponse.ok("Xoá chủ đề thành công", null);
    }

    @Operation(summary = "Danh sách rubric kèm các tiêu chí")
    @GetMapping("/rubrics")
    public ApiResponse<List<RubricResponse>> listRubrics(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(contentService.listRubrics(principal));
    }

    @Operation(summary = "Tạo rubric thủ công kèm danh sách tiêu chí")
    @PostMapping("/rubrics")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RubricResponse> createRubric(
            @Valid @RequestBody CreateRubricRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok("Tạo rubric thành công", contentService.createRubric(principal, request));
    }

    @Operation(summary = "Cập nhật rubric và các tiêu chí")
    @PutMapping("/rubrics/{id}")
    public ApiResponse<RubricResponse> updateRubric(
            @PathVariable String id,
            @Valid @RequestBody UpdateRubricRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok("Cập nhật rubric thành công", contentService.updateRubric(principal, id, request));
    }

    @Operation(summary = "Xoá rubric (chỉ xoá được khi chưa có câu hỏi nào sử dụng)")
    @DeleteMapping("/rubrics/{id}")
    public ApiResponse<Void> deleteRubric(
            @PathVariable String id,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        contentService.deleteRubric(principal, id);
        return ApiResponse.ok("Xoá rubric thành công", null);
    }

    @Operation(summary = "Danh sách câu hỏi, lọc theo chủ đề nếu có topicId")
    @GetMapping("/questions")
    public ApiResponse<List<QuestionResponse>> listQuestions(
            @RequestParam(required = false) String topicId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(contentService.listQuestions(principal, topicId));
    }

    @Operation(summary = "Tạo câu hỏi thủ công")
    @PostMapping("/questions")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<QuestionResponse> createQuestion(
            @Valid @RequestBody CreateQuestionRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok("Tạo câu hỏi thành công", contentService.createQuestion(principal, request));
    }

    @Operation(summary = "Cập nhật câu hỏi")
    @PutMapping("/questions/{id}")
    public ApiResponse<QuestionResponse> updateQuestion(
            @PathVariable String id,
            @Valid @RequestBody UpdateQuestionRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok("Cập nhật câu hỏi thành công", contentService.updateQuestion(principal, id, request));
    }

    @Operation(summary = "Xoá câu hỏi")
    @DeleteMapping("/questions/{id}")
    public ApiResponse<Void> deleteQuestion(
            @PathVariable String id,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        contentService.deleteQuestion(principal, id);
        return ApiResponse.ok("Xoá câu hỏi thành công", null);
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
