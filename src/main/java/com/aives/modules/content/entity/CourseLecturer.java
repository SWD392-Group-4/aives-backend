package com.aives.modules.content.entity;

import com.aives.common.entity.BaseEntity;
import com.aives.modules.auth.entity.User;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "course_lecturers",
        uniqueConstraints = @UniqueConstraint(name = "uq_course_lecturers", columnNames = {"courses_id", "users_id"}))
@AttributeOverride(
        name = "id",
        column = @Column(name = "course_lecturers_id", nullable = false, updatable = false, length = 20))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourseLecturer extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "courses_id", referencedColumnName = "courses_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "users_id", referencedColumnName = "users_id", nullable = false)
    private User user;
}
