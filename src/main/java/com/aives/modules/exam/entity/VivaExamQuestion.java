package com.aives.modules.exam.entity;

import com.aives.common.entity.BaseEntity;
import com.aives.modules.content.entity.Question;
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

@Entity
@Table(
        name = "viva_exam_questions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_viva_exam_questions_exam_question", columnNames = {"viva_exams_id", "questions_id"}),
                @UniqueConstraint(name = "uq_viva_exam_questions_exam_order", columnNames = {"viva_exams_id", "order_no"})
        })
@AttributeOverride(
        name = "id",
        column = @Column(name = "viva_exam_questions_id", nullable = false, updatable = false, length = 20))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VivaExamQuestion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "viva_exams_id", referencedColumnName = "viva_exams_id", nullable = false)
    private VivaExam exam;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "questions_id", referencedColumnName = "questions_id", nullable = false)
    private Question question;

    @Column(name = "order_no", nullable = false)
    private Integer orderNo;

    @Builder.Default
    @Column(name = "weight", nullable = false, precision = 5, scale = 2)
    private BigDecimal weight = BigDecimal.ONE;
}
