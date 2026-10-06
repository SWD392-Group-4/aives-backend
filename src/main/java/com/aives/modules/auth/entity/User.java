package com.aives.modules.auth.entity;

import com.aives.common.entity.BaseEntity;
import com.aives.modules.auth.enums.RoleName;
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
import org.hibernate.annotations.Nationalized;

@Entity
@Table(
        name = "users",
        uniqueConstraints = @UniqueConstraint(name = "UQ_users_email", columnNames = "email"))
// Cot khoa chinh trong DB ten la users_id (BaseEntity mac dinh la "id")
@AttributeOverride(
        name = "id",
        column = @Column(name = "users_id", nullable = false, updatable = false, length = 20))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Nationalized
    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    // Moi user co dung 1 role. EAGER vi lan nao doc user cung can biet role (phan quyen).
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "roles_id", referencedColumnName = "roles_id", nullable = false)
    private Role role;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    // true: tai khoan do admin tao, dang dung mat khau tam -> phai doi mat khau truoc khi dung he thong
    @Builder.Default
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = false;

    public RoleName getRoleName() {
        return role.getName();
    }
}
