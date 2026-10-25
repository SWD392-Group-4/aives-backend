package com.aives.modules.content.entity;

import com.aives.common.entity.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.Length;
import org.hibernate.annotations.Nationalized;

@Entity
@Table(name = "questions")
@AttributeOverride(
        name = "id",
        column = @Column(name = "questions_id", nullable = false, updatable = false, length = 20))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Question extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topics_id", referencedColumnName = "topics_id")
    private Topic topic;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chapters_id", referencedColumnName = "chapters_id")
    private Chapter chapter;

    @Column(name = "created_by", length = 20)
    private String createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rubrics_id", referencedColumnName = "rubrics_id")
    private Rubric rubric;

    @Nationalized
    @Column(name = "content", nullable = false, length = Length.LONG32)
    private String content;

    @Builder.Default
    @Column(name = "difficulty", length = 20)
    private String difficulty = "MEDIUM";

    @Column(name = "bloom_level", length = 20)
    private String bloomLevel;

    @Nationalized
    @Column(name = "model_answer", length = Length.LONG32)
    private String modelAnswer;

    @Column(name = "expected_keywords", length = Length.LONG32)
    private String expectedKeywords;

    @Builder.Default
    @Column(name = "source", nullable = false, length = 20)
    private String source = "MANUAL";

    @Builder.Default
    @Column(name = "status", nullable = false, length = 20)
    private String status = "DRAFT";

    @Column(name = "question_audio_key", length = 500)
    private String questionAudioKey;
}
