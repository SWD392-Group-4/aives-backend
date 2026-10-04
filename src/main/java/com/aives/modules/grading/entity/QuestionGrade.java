package com.aives.modules.grading.entity;

import com.aives.common.entity.BaseEntity;
import com.aives.modules.exam.entity.VivaAttempt;
import com.aives.modules.exam.entity.VivaExamQuestion;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

@Entity
@Table(
        name = "question_grades",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_question_grades_attempt_question",
                columnNames = {"viva_attempts_id", "viva_exam_questions_id"}))
@AttributeOverride(
        name = "id",
        column = @Column(name = "question_grades_id", nullable = false, updatable = false, length = 20))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionGrade extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "viva_attempts_id", referencedColumnName = "viva_attempts_id", nullable = false)
    private VivaAttempt attempt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "viva_exam_questions_id", referencedColumnName = "viva_exam_questions_id", nullable = false)
    private VivaExamQuestion examQuestion;

    @Column(name = "ai_score", precision = 5, scale = 2)
    private BigDecimal aiScore;

    @Column(name = "final_score", precision = 5, scale = 2)
    private BigDecimal finalScore;

    @Nationalized
    @Column(name = "ai_strengths", columnDefinition = "TEXT")
    private String aiStrengths;

    @Nationalized
    @Column(name = "ai_weaknesses", columnDefinition = "TEXT")
    private String aiWeaknesses;

    @Nationalized
    @Column(name = "ai_feedback", columnDefinition = "TEXT")
    private String aiFeedback;

    @Nationalized
    @Column(name = "lecturer_note", columnDefinition = "TEXT")
    private String lecturerNote;

    @Builder.Default
    @Column(name = "status", nullable = false, length = 20)
    private String status = "AI_DRAFT";

    @Column(name = "graded_by", length = 20)
    private String gradedBy;

    @Column(name = "graded_at")
    private LocalDateTime gradedAt;
}
