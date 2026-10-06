package com.aives.modules.vivaroom.entity;

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
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

@Entity
@Table(name = "interview_exchanges")
@AttributeOverride(
        name = "id",
        column = @Column(name = "interview_exchanges_id", nullable = false, updatable = false, length = 20))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewExchange extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "viva_attempts_id", referencedColumnName = "viva_attempts_id", nullable = false)
    private VivaAttempt attempt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "viva_exam_questions_id", referencedColumnName = "viva_exam_questions_id", nullable = false)
    private VivaExamQuestion examQuestion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_exchange_id", referencedColumnName = "interview_exchanges_id")
    private InterviewExchange parentExchange;

    @Builder.Default
    @Column(name = "depth", nullable = false)
    private Integer depth = 0;

    @Nationalized
    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Column(name = "question_audio_key", length = 500)
    private String questionAudioKey;

    @Nationalized
    @Column(name = "transcript", columnDefinition = "TEXT")
    private String transcript;

    @Column(name = "answer_audio_key", length = 500)
    private String answerAudioKey;

    @Column(name = "follow_up_reason", length = 20)
    private String followUpReason;

    @Column(name = "ai_decision", length = 20)
    private String aiDecision;

    @Nationalized
    @Column(name = "ai_decision_reason", columnDefinition = "TEXT")
    private String aiDecisionReason;

    @Column(name = "answer_started_at")
    private LocalDateTime answerStartedAt;

    @Column(name = "answer_ended_at")
    private LocalDateTime answerEndedAt;

    @Column(name = "latency_ms")
    private Integer latencyMs;
}
