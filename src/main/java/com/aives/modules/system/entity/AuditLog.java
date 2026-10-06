package com.aives.modules.system.entity;

import com.aives.common.entity.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.Length;

@Entity
@Table(name = "audit_logs")
@AttributeOverride(
        name = "id",
        column = @Column(name = "audit_logs_id", nullable = false, updatable = false, length = 20))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog extends BaseEntity {

    @Column(name = "actor_id", nullable = false, length = 20)
    private String actorId;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id", nullable = false, length = 50)
    private String entityId;

    @Column(name = "old_value", length = Length.LONG32)
    private String oldValue;

    @Column(name = "new_value", length = Length.LONG32)
    private String newValue;
}
