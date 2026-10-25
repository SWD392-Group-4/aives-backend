package com.aives.modules.content.service;

import com.aives.common.dto.PageResponse;
import com.aives.common.security.UserPrincipal;
import com.aives.modules.content.dto.CreateChapterQuestionRequest;
import com.aives.modules.content.dto.QuestionResponse;
import com.aives.modules.content.dto.UpdateQuestionRequest;
import org.springframework.data.domain.Pageable;

public interface QuestionBankService {

    PageResponse<QuestionResponse> getQuestions(
            String chapterId, String status, String difficulty, Pageable pageable);

    QuestionResponse createManualQuestion(
            String chapterId, CreateChapterQuestionRequest request, UserPrincipal principal);

    QuestionResponse getQuestionDetail(String questionId);

    QuestionResponse updateQuestion(
            String questionId, UpdateQuestionRequest request, UserPrincipal principal);

    void deleteQuestion(String questionId, UserPrincipal principal);

    QuestionResponse approveQuestion(String questionId, UserPrincipal principal);

    QuestionResponse rejectQuestion(String questionId, String reason, UserPrincipal principal);
}
