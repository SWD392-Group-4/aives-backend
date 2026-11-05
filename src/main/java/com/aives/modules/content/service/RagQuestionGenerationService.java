package com.aives.modules.content.service;

import com.aives.common.security.UserPrincipal;
import com.aives.modules.content.dto.GenerateQuestionsRequest;
import com.aives.modules.content.dto.QuestionResponse;
import java.util.List;

public interface RagQuestionGenerationService {

    List<QuestionResponse> generateQuestions(
            String chapterId, GenerateQuestionsRequest request, UserPrincipal principal);

    /** Sinh cau hoi cho ca mon: moi chuong duoc chon sinh tu tai lieu READY cua chinh chuong do. */
    List<QuestionResponse> generateCourseQuestions(
            String courseId, com.aives.modules.content.dto.GenerateCourseQuestionsRequest request, UserPrincipal principal);
}
