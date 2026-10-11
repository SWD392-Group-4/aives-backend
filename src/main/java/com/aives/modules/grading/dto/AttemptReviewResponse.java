package com.aives.modules.grading.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Chi tiet 1 luot thi de tham dinh / xem ket qua. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttemptReviewResponse {
    private GradingAttemptResponse attempt;
    private List<QuestionGradeResponse> questionGrades;
}
