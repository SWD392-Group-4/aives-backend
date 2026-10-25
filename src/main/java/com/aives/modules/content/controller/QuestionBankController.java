package com.aives.modules.content.controller;

import com.aives.common.dto.ApiResponse;
import com.aives.common.dto.PageResponse;
import com.aives.common.security.UserPrincipal;
import com.aives.modules.content.dto.CreateChapterQuestionRequest;
import com.aives.modules.content.dto.GenerateQuestionsRequest;
import com.aives.modules.content.dto.QuestionResponse;
import com.aives.modules.content.dto.RejectQuestionRequest;
import com.aives.modules.content.dto.UpdateQuestionRequest;
import com.aives.modules.content.service.QuestionBankService;
import com.aives.modules.content.service.RagQuestionGenerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Question Bank", description = "Ngân hàng câu hỏi dùng chung & AI RAG Question Generation (BR-BANK-001, BR-BANK-002, BR-BANK-004, BR-BANK-005)")
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('LECTURER', 'ADMIN')")
@RequiredArgsConstructor
public class QuestionBankController {

    private final QuestionBankService questionBankService;
    private final RagQuestionGenerationService ragQuestionGenerationService;

    @Operation(summary = "Yêu cầu AI sinh câu hỏi từ tài liệu chương theo tỷ lệ Dễ / Trung bình / Khó (BR-BANK-002, BR-BANK-004)")
    @PostMapping("/chapters/{chapterId}/questions/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<List<QuestionResponse>> generateChapterQuestions(
            @PathVariable String chapterId,
            @Valid @RequestBody GenerateQuestionsRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        List<QuestionResponse> questions = ragQuestionGenerationService.generateQuestions(chapterId, request, principal);
        return ApiResponse.ok("Sinh câu hỏi thành công (trạng thái DRAFT)", questions);
    }

    @Operation(summary = "Lấy danh sách câu hỏi trong ngân hàng dùng chung của chương (BR-BANK-005)")
    @GetMapping("/chapters/{chapterId}/questions")
    public ApiResponse<PageResponse<QuestionResponse>> getChapterQuestions(
            @PathVariable String chapterId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String difficulty,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ApiResponse.ok(questionBankService.getQuestions(chapterId, status, difficulty, pageable));
    }

    @Operation(summary = "Giảng viên tự soạn câu hỏi thủ công vào ngân hàng chung")
    @PostMapping("/chapters/{chapterId}/questions")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<QuestionResponse> createManualQuestion(
            @PathVariable String chapterId,
            @Valid @RequestBody CreateChapterQuestionRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        QuestionResponse response = questionBankService.createManualQuestion(chapterId, request, principal);
        return ApiResponse.ok("Tạo câu hỏi thành công", response);
    }

    @Operation(summary = "Xem chi tiết câu hỏi")
    @GetMapping("/questions/{questionId}")
    public ApiResponse<QuestionResponse> getQuestionDetail(
            @PathVariable String questionId) {
        return ApiResponse.ok(questionBankService.getQuestionDetail(questionId));
    }

    @Operation(summary = "Chỉnh sửa nội dung, độ khó, đáp án mẫu, từ khóa câu hỏi")
    @PutMapping("/questions/{questionId}")
    public ApiResponse<QuestionResponse> updateQuestion(
            @PathVariable String questionId,
            @Valid @RequestBody UpdateQuestionRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        QuestionResponse response = questionBankService.updateQuestion(questionId, request, principal);
        return ApiResponse.ok("Cập nhật câu hỏi thành công", response);
    }

    @Operation(summary = "Xóa câu hỏi khỏi ngân hàng")
    @DeleteMapping("/questions/{questionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> deleteQuestion(
            @PathVariable String questionId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        questionBankService.deleteQuestion(questionId, principal);
        return ApiResponse.ok("Xóa câu hỏi thành công", null);
    }

    @Operation(summary = "Phê duyệt câu hỏi đưa vào ngân hàng chính thức (DRAFT -> APPROVED, BR-BANK-001 Gate)")
    @PatchMapping("/questions/{questionId}/approve")
    public ApiResponse<QuestionResponse> approveQuestion(
            @PathVariable String questionId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        QuestionResponse response = questionBankService.approveQuestion(questionId, principal);
        return ApiResponse.ok("Phê duyệt câu hỏi thành công", response);
    }

    @Operation(summary = "Từ chối câu hỏi (DRAFT -> REJECTED)")
    @PatchMapping("/questions/{questionId}/reject")
    public ApiResponse<QuestionResponse> rejectQuestion(
            @PathVariable String questionId,
            @RequestBody(required = false) RejectQuestionRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        String reason = (request != null) ? request.getReason() : null;
        QuestionResponse response = questionBankService.rejectQuestion(questionId, reason, principal);
        return ApiResponse.ok("Từ chối câu hỏi thành công", response);
    }
}
