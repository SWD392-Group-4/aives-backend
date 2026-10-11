package com.aives.modules.grading.service;

import com.aives.common.security.UserPrincipal;
import com.aives.modules.grading.dto.AttemptReviewResponse;
import com.aives.modules.grading.dto.GradingAttemptResponse;
import com.aives.modules.grading.dto.QuestionGradeResponse;
import java.math.BigDecimal;
import java.util.List;

/** Tham dinh diem (giang vien, Human-in-the-Loop) va xem ket qua (sinh vien). */
public interface GradingService {

    /** Cac luot thi da thi xong cua nhung phien thi do giang vien tao (admin: moi phien). */
    List<GradingAttemptResponse> listAttemptsForReview(UserPrincipal actor);

    AttemptReviewResponse getReview(UserPrincipal actor, String attemptId);

    /** Giang vien chot diem 1 cau. Lech qua 2 diem so voi diem AI thi bat buoc co ghi chu (BR-GRADE-002). */
    QuestionGradeResponse saveQuestionGrade(UserPrincipal actor, String questionGradeId, BigDecimal finalScore, String lecturerNote);

    /** Cong bo ket qua cho sinh vien. Moi cau phai duoc giang vien chot diem truoc (BR-GRADE-003). */
    GradingAttemptResponse publish(UserPrincipal actor, String attemptId);

    /** Xoa cac diem giang vien CHUA chot va de AI cham lai nhung cau do. */
    GradingAttemptResponse requestRegrade(UserPrincipal actor, String attemptId);

    List<GradingAttemptResponse> listMyAttempts(String studentId);

    /** Sinh vien xem ket qua luot thi cua minh, chi khi da cong bo. */
    AttemptReviewResponse getMyResult(String studentId, String attemptId);
}
