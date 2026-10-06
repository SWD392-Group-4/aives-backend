package com.aives.modules.grading.entity;

import com.aives.common.entity.BaseEntity;
import com.aives.modules.content.entity.RubricCriterion;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.Length;
import org.hibernate.annotations.Nationalized;

@Entity
@Table(
        name = "criteria_grades",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_criteria_grades_question_criteria",
                columnNames = {"question_grades_id", "rubric_criteria_id"}))
@AttributeOverride(
        name = "id",
        column = @Column(name = "criteria_grades_id", nullable = false, updatable = false, length = 20))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CriteriaGrade extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_grades_id", referencedColumnName = "question_grades_id", nullable = false)
    private QuestionGrade questionGrade;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rubric_criteria_id", referencedColumnName = "rubric_criteria_id", nullable = false)
    private RubricCriterion rubricCriterion;

    @Column(name = "ai_score", precision = 5, scale = 2)
    private BigDecimal aiScore;

    @Column(name = "final_score", precision = 5, scale = 2)
    private BigDecimal finalScore;

    @Nationalized
    @Column(name = "evidence_quote", length = Length.LONG32)
    private String evidenceQuote;

    @Nationalized
    @Column(name = "rationale", length = Length.LONG32)
    private String rationale;
}
