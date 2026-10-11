package com.aives.modules.content.entity;

import com.aives.common.entity.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@Table(name = "rubric_criteria")
@AttributeOverride(
        name = "id",
        column = @Column(name = "rubric_criteria_id", nullable = false, updatable = false, length = 20))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RubricCriterion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rubrics_id", referencedColumnName = "rubrics_id", nullable = false)
    private Rubric rubric;

    @Nationalized
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Nationalized
    @Column(name = "description", nullable = false, length = Length.LONG32)
    private String description;

    @Column(name = "weight_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal weightPercent;

    @Builder.Default
    @Column(name = "order_no", nullable = false)
    private Integer orderNo = 1;
}
