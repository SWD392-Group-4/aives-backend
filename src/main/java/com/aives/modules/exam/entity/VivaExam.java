package com.aives.modules.exam.entity;

import com.aives.common.entity.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.Length;
import org.hibernate.annotations.Nationalized;

/**
 * Phien thi (VIVA_EXAM trong ERD).
 * Moi cot thoi gian cua phien thi (start_at, end_at, cancelled_at) luu theo gio UTC.
 */
@Entity
@Table(name = "viva_exams")
// Khoa chinh viva_exams_id cung chinh la MA VAO THI sinh vien nhap: AIVES_EXAM_yyyy_xxxxxx (22 ky tu),
// nen cot dai 30 thay vi 20 nhu cac bang khac. Khong con cot exam_code rieng.
@AttributeOverride(
        name = "id",
        column = @Column(name = "viva_exams_id", nullable = false, updatable = false, length = 30))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VivaExam extends BaseEntity {

    // Nguoi tao phien (LECTURER hoac ADMIN). Chi luu id vi User thuoc module auth.
    @Column(name = "lecturer_id", nullable = false, length = 20)
    private String lecturerId;

    @Nationalized
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Nationalized
    @Column(name = "description", length = 2000)
    private String description;

    // Ma truy cap 6 chu so, dung chung cho ca phien. Nguoi tao xem lai va tao lai duoc.
    @Column(name = "passcode", nullable = false, length = 6)
    private String passcode;

    // Thoi luong lam bai cua moi sinh vien (phut)
    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    // So luot thi toi da cua MOI sinh vien trong phien nay (1 = chi duoc thi 1 lan)
    @Builder.Default
    @Column(name = "max_attempts", nullable = false)
    private Integer maxAttempts = 1;

    @Builder.Default
    @Column(name = "max_follow_up_per_question", nullable = false)
    private Integer maxFollowUpPerQuestion = 2;

    @Builder.Default
    @Column(name = "prepare_seconds", nullable = false)
    private Integer prepareSeconds = 30;

    @Builder.Default
    @Column(name = "answer_seconds", nullable = false)
    private Integer answerSeconds = 120;

    @Column(name = "domain_keywords", length = Length.LONG32)
    private String domainKeywords;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    // So cau hoi goc cua phien. null = dung so mac dinh (app.viva.default-question-count)
    @Column(name = "question_count")
    private Integer questionCount;

    // Chu de giang vien chon cho phien: cau hoi cua phien duoc boc ngau nhien tu cac chu de nay.
    // Chi luu id vi Topic thuoc module content. Rong = phien cu, lay cau hoi theo cach mac dinh.
    @Builder.Default
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "viva_exam_topics",
            joinColumns = @JoinColumn(name = "viva_exams_id", referencedColumnName = "viva_exams_id"))
    @Column(name = "topics_id", nullable = false, length = 20)
    private Set<String> topicIds = new LinkedHashSet<>();

    // Khac null nghia la phien da bi huy
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    public boolean isCancelled() {
        return cancelledAt != null;
    }
}
