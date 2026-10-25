package com.aives.modules.content.service;

import com.aives.common.security.UserPrincipal;
import com.aives.modules.content.dto.GenerateQuestionsRequest;
import com.aives.modules.content.dto.QuestionResponse;
import java.util.List;

public interface RagQuestionGenerationService {

    List<QuestionResponse> generateQuestions(
            String chapterId, GenerateQuestionsRequest request, UserPrincipal principal);
}
