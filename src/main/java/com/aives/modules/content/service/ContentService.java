package com.aives.modules.content.service;

import com.aives.common.security.UserPrincipal;
import com.aives.modules.content.dto.LessonResponse;
import com.aives.modules.content.dto.QuestionFigureResponse;
import com.aives.modules.content.dto.QuestionResponse;
import com.aives.modules.content.dto.RubricResponse;
import com.aives.modules.content.dto.TopicResponse;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Ngan hang cau hoi va rubric. Giang vien chi thay va sua du lieu cua minh (BR-AUTH-001), admin thay tat ca.
 */
public interface ContentService {

    List<LessonResponse> listLessons(UserPrincipal actor);

    List<TopicResponse> listTopics(UserPrincipal actor, String lessonId);

    /**
     * Cac chu de trong topicIds ma actor duoc dung (cua minh, admin: tat ca), kem so cau da duyet.
     * Id khong ton tai hoac khong co quyen thi khong co trong ket qua. Dung khi tao / sua phien thi.
     */
    List<TopicResponse> getTopicsByIds(UserPrincipal actor, Collection<String> topicIds);

    /** Ten chu de theo id, KHONG kiem tra quyen (chi dung de hien thi ten chu de cua phien thi). */
    Map<String, String> getTopicNames(Collection<String> topicIds);

    List<RubricResponse> listRubrics(UserPrincipal actor);

    List<QuestionResponse> listQuestions(UserPrincipal actor, String topicId);

    /** Doi trang thai cau hoi (duyet, tu choi, luu tru...). Duyet thi cau hoi phai co rubric hop le (BR-BANK-001). */
    QuestionResponse changeQuestionStatus(UserPrincipal actor, String questionId, String status, String rubricId);

    /** Hinh ve cua cau hoi (cau hoi cua minh, admin: tat ca). Khong co hinh thi bao QUESTION_FIGURE_NOT_FOUND. */
    QuestionFigureResponse getQuestionFigure(UserPrincipal actor, String questionId);

    /**
     * Hinh ve cua cau hoi dang data URL, KHONG kiem tra quyen: chi dung o phong thi, sau khi da kiem tra
     * sinh vien dang thi cau hoi do.
     */
    Optional<String> findQuestionFigureDataUrl(String questionId);
}
