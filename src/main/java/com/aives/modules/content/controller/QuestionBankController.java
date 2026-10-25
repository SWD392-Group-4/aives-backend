package com.aives.modules.content.controller;

import com.aives.common.dto.ApiResponse;
import com.aives.common.security.UserPrincipal;
import com.aives.modules.content.dto.CreateQuestionRequest;
import com.aives.modules.content.dto.GenerateQuestionsRequest;
import com.aives.modules.content.dto.QuestionFilter;
import com.aives.modules.content.dto.QuestionResponse;
import com.aives.modules.content.dto.RejectQuestionRequest;
import com.aives.modules.content.dto.UpdateQuestionRequest;
import com.aives.modules.content.enums.QuestionDifficulty;
import com.aives.modules.content.enums.QuestionStatus;
import com.aives.modules.content.service.IQuestionBankService;
import com.aives.modules.content.service.IRagQuestionGenerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Question Bank", description = "Ngân hàng câu hỏi dùng chung và sinh câu hỏi bằng RAG")
@RestController
@RequiredArgsConstructor
public class QuestionBankController {

    private final IRagQuestionGenerationService ragQuestionService;
    private final IQuestionBankService questionBankService;

    @Operation(summary = "Yêu cầu AI sinh câu hỏi từ tài liệu chương theo tỷ lệ Dễ / Trung bình / Khó",
            description = "Gọi Vertex AI RAG truy xuất chunks, gọi Gemini 1.5 Flash sinh câu hỏi DRAFT. Tuyệt đối không sinh Rubric (BR-BANK-002, BR-BANK-004).")
    @PreAuthorize("hasAnyRole('LECTURER', 'ADMIN')")
    @PostMapping("/api/v1/chapters/{chapterId}/questions/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<List<QuestionResponse>> generateChapterQuestions(
            @Parameter(description = "Mã chương") @PathVariable String chapterId,
            @Valid @RequestBody GenerateQuestionsRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        List<QuestionResponse> response = ragQuestionService.generateQuestions(chapterId, request, principal.getId());
        return ApiResponse.ok("Sinh câu hỏi thành công", response);
    }

    @Operation(summary = "Lấy danh sách câu hỏi trong ngân hàng dùng chung của chương",
            description = "Cho phép tất cả giảng viên cùng dạy môn xem câu hỏi chung theo bộ lọc trạng thái và độ khó (BR-BANK-005).")
    @GetMapping("/api/v1/chapters/{chapterId}/questions")
    public ApiResponse<List<QuestionResponse>> getChapterQuestions(
            @Parameter(description = "Mã chương") @PathVariable String chapterId,
            @RequestParam(required = false) QuestionStatus status,
            @RequestParam(required = false) QuestionDifficulty difficulty,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        QuestionFilter filter = QuestionFilter.builder().status(status).difficulty(difficulty).build();
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<QuestionResponse> responsePage = questionBankService.getQuestions(chapterId, filter, pageRequest);
        return ApiResponse.ok("Lấy danh sách câu hỏi thành công", responsePage.getContent());
    }

    @Operation(summary = "Giảng viên tự soạn câu hỏi thủ công vào ngân hàng")
    @PreAuthorize("hasAnyRole('LECTURER', 'ADMIN')")
    @PostMapping("/api/v1/chapters/{chapterId}/questions")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<QuestionResponse> createManualQuestion(
            @Parameter(description = "Mã chương") @PathVariable String chapterId,
            @Valid @RequestBody CreateQuestionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        QuestionResponse response = questionBankService.createManualQuestion(chapterId, request, principal.getId());
        return ApiResponse.ok("Tạo câu hỏi thành công", response);
    }

    @Operation(summary = "Xem chi tiết câu hỏi")
    @GetMapping("/api/v1/questions/{questionId}")
    public ApiResponse<QuestionResponse> getQuestionDetail(
            @Parameter(description = "Mã câu hỏi") @PathVariable String questionId) {
        QuestionResponse response = questionBankService.getQuestionById(questionId);
        return ApiResponse.ok("Chi tiết câu hỏi", response);
    }

    @Operation(summary = "Chỉnh sửa nội dung, độ khó, đáp án mẫu, từ khóa câu hỏi")
    @PreAuthorize("hasAnyRole('LECTURER', 'ADMIN')")
    @PutMapping("/api/v1/questions/{questionId}")
    public ApiResponse<QuestionResponse> updateQuestion(
            @Parameter(description = "Mã câu hỏi") @PathVariable String questionId,
            @Valid @RequestBody UpdateQuestionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        QuestionResponse response = questionBankService.updateQuestion(questionId, request, principal.getId());
        return ApiResponse.ok("Cập nhật thành công", response);
    }

    @Operation(summary = "Xóa câu hỏi khỏi ngân hàng")
    @PreAuthorize("hasAnyRole('LECTURER', 'ADMIN')")
    @DeleteMapping("/api/v1/questions/{questionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteQuestion(
            @Parameter(description = "Mã câu hỏi") @PathVariable String questionId,
            @AuthenticationPrincipal UserPrincipal principal) {
        questionBankService.deleteQuestion(questionId, principal.getId());
    }

    @Operation(summary = "Phê duyệt câu hỏi đưa vào ngân hàng chính thức (DRAFT -> APPROVED)",
            description = "Kiểm tra điều kiện bất biến (BR-BANK-001) - môn học bắt buộc phải có Rubric hoàn chỉnh 100% mới cho phép duyệt.")
    @PreAuthorize("hasAnyRole('LECTURER', 'ADMIN')")
    @PatchMapping("/api/v1/questions/{questionId}/approve")
    public ApiResponse<QuestionResponse> approveQuestion(
            @Parameter(description = "Mã câu hỏi") @PathVariable String questionId,
            @AuthenticationPrincipal UserPrincipal principal) {
        QuestionResponse response = questionBankService.approveQuestion(questionId, principal.getId());
        return ApiResponse.ok("Phê duyệt thành công", response);
    }

    @Operation(summary = "Từ chối câu hỏi (DRAFT -> REJECTED)")
    @PreAuthorize("hasAnyRole('LECTURER', 'ADMIN')")
    @PatchMapping("/api/v1/questions/{questionId}/reject")
    public ApiResponse<QuestionResponse> rejectQuestion(
            @Parameter(description = "Mã câu hỏi") @PathVariable String questionId,
            @RequestBody(required = false) RejectQuestionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        String reason = request != null ? request.getReason() : null;
        QuestionResponse response = questionBankService.rejectQuestion(questionId, reason, principal.getId());
        return ApiResponse.ok("Từ chối câu hỏi thành công", response);
    }
}