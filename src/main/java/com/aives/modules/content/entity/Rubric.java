package com.aives.modules.content.entity;

import com.aives.common.entity.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.Length;
import org.hibernate.annotations.Nationalized;

@Entity
@Table(name = "rubrics")
@AttributeOverride(
        name = "id",
        column = @Column(name = "rubrics_id", nullable = false, updatable = false, length = 20))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Rubric extends BaseEntity {

    @Column(name = "created_by", nullable = false, length = 20)
    private String createdBy;

    @jakarta.persistence.ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @jakarta.persistence.JoinColumn(name = "courses_id", referencedColumnName = "courses_id")
    private Course course;

    @Builder.Default
    @Column(name = "is_locked", nullable = false)
    private Boolean isLocked = false;

    @Nationalized
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Nationalized
    @Column(name = "description", length = Length.LONG32)
    private String description;

    @Builder.Default
    @Column(name = "max_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxScore = new BigDecimal("10.00");

    /** Thoi diem giang vien duyet rubric. null = rubric con nhap, chua duoc sinh / duyet cau hoi. */
    @Column(name = "approved_at")
    private java.time.LocalDateTime approvedAt;

    @Column(name = "approved_by", length = 20)
    private String approvedBy;
}
