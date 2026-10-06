package com.aives.modules.auth.entity;

import com.aives.common.entity.BaseEntity;
import com.aives.modules.auth.enums.RoleName;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

/**
 * Bang roles: danh muc role cua he thong (ADMIN / LECTURER / STUDENT).
 * Moi user tro toi dung 1 role qua users.roles_id.
 * roles_id la 2 chu cai (AD / LE / ST), cung la tien to cua users_id.
 */
@Entity
@Table(
        name = "roles",
        uniqueConstraints = @UniqueConstraint(name = "UQ_roles_name", columnNames = "name"))
@AttributeOverride(
        name = "id",
        column = @Column(name = "roles_id", nullable = false, updatable = false, length = 20))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "name", nullable = false, length = 20)
    private RoleName name;

    @Nationalized
    @Column(name = "description", length = 255)
    private String description;
}
