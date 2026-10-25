package com.aives.modules.content.service;

import com.aives.modules.content.dto.CreateQuestionRequest;
import com.aives.modules.content.dto.QuestionFilter;
import com.aives.modules.content.dto.QuestionResponse;
import com.aives.modules.content.dto.UpdateQuestionRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface IQuestionBankService {

    Page<QuestionResponse> getQuestions(String chapterId, QuestionFilter filter, Pageable pageable);

    QuestionResponse getQuestionById(String questionId);

    QuestionResponse createManualQuestion(String chapterId, CreateQuestionRequest request, String lecturerId);

    QuestionResponse updateQuestion(String questionId, UpdateQuestionRequest request, String lecturerId);

    QuestionResponse approveQuestion(String questionId, String lecturerId);

    QuestionResponse rejectQuestion(String questionId, String reason, String lecturerId);

    void deleteQuestion(String questionId, String lecturerId);
}