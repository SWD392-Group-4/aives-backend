package com.aives.modules.content.service;

import com.aives.modules.content.dto.GenerateQuestionsRequest;
import com.aives.modules.content.dto.QuestionResponse;
import java.util.List;

public interface IRagQuestionGenerationService {

    List<QuestionResponse> generateQuestions(String chapterId, GenerateQuestionsRequest request, String lecturerId);
}