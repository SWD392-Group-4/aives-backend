package com.aives.modules.content.service;

import com.aives.common.security.UserPrincipal;
import com.aives.modules.content.dto.LessonResponse;
import com.aives.modules.content.dto.QuestionResponse;
import com.aives.modules.content.dto.RubricResponse;
import com.aives.modules.content.dto.TopicResponse;
import java.util.List;

/**
 * Ngan hang cau hoi va rubric. Giang vien chi thay va sua du lieu cua minh (BR-AUTH-001), admin thay tat ca.
 */
public interface ContentService {

    List<LessonResponse> listLessons(UserPrincipal actor);

    List<TopicResponse> listTopics(UserPrincipal actor, String lessonId);

    List<RubricResponse> listRubrics(UserPrincipal actor);

    List<QuestionResponse> listQuestions(UserPrincipal actor, String topicId);

    /** Doi trang thai cau hoi (duyet, tu choi, luu tru...). Duyet thi cau hoi phai co rubric hop le (BR-BANK-001). */
    QuestionResponse changeQuestionStatus(UserPrincipal actor, String questionId, String status, String rubricId);
}
