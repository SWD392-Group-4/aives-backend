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

    @Nationalized
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Nationalized
    @Column(name = "description", length = Length.LONG32)
    private String description;

    @Builder.Default
    @Column(name = "max_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxScore = new BigDecimal("10.00");
}
