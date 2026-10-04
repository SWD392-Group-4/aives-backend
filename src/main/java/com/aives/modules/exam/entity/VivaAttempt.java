package com.aives.modules.exam.entity;

import com.aives.common.entity.BaseEntity;
import com.aives.modules.exam.enums.AttemptStatus;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Luot thi cua 1 sinh vien trong 1 phien thi (VIVA_ATTEMPT trong ERD).
 * Moi sinh vien chi co 1 luot / phien. Thoi gian luu theo gio UTC.
 */
@Entity
// Cot khoa chinh trong DB ten la viva_attempts_id (BaseEntity mac dinh la "id")
@AttributeOverride(
        name = "id",
        column = @Column(name = "viva_attempts_id", nullable = false, updatable = false, length = 20))
@Table(
        name = "viva_attempts",
        uniqueConstraints = @UniqueConstraint(
                name = "UQ_viva_attempts_exam_student",
                columnNames = {"viva_exams_id", "student_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VivaAttempt extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    // Khoa ngoai mang dung ten khoa chinh ma no tro toi
    @JoinColumn(name = "viva_exams_id", referencedColumnName = "viva_exams_id", nullable = false)
    private VivaExam exam;

    // Chi luu id vi User thuoc module auth
    @Column(name = "student_id", nullable = false, length = 20)
    private String studentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AttemptStatus status;

    @Builder.Default
    @Column(name = "result_status", nullable = false, length = 20)
    private String resultStatus = "NONE";

    @Column(name = "total_ai_score", precision = 6, scale = 2)
    private java.math.BigDecimal totalAiScore;

    @Column(name = "total_final_score", precision = 6, scale = 2)
    private java.math.BigDecimal totalFinalScore;

    @Column(name = "full_audio_key", length = 500)
    private String fullAudioKey;

    // Thoi diem sinh vien vao phien
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    // Han lam bai = min(started_at + duration, end_at cua phien). Chot luc vao, khong doi ve sau.
    @Column(name = "deadline_at", nullable = false)
    private LocalDateTime deadlineAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;
}
