package com.aives.modules.exam.entity;

import com.aives.common.entity.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    // Khac null nghia la phien da bi huy
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    public boolean isCancelled() {
        return cancelledAt != null;
    }
}
