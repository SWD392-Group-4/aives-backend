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
@Table(name = "chapters")
@AttributeOverride(
        name = "id",
        column = @Column(name = "chapters_id", nullable = false, updatable = false, length = 20))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Chapter extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "courses_id", referencedColumnName = "courses_id", nullable = false)
    private Course course;

    @Nationalized
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Builder.Default
    @Column(name = "order_no", nullable = false)
    private int orderNo = 1;

    @Nationalized
    @Column(name = "description", length = Length.LONG32)
    private String description;
}
