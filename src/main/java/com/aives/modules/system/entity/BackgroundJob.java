package com.aives.modules.system.entity;

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
import org.hibernate.Length;

@Entity
@Table(name = "background_jobs")
@AttributeOverride(
        name = "id",
        column = @Column(name = "background_jobs_id", nullable = false, updatable = false, length = 20))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BackgroundJob extends BaseEntity {

    @Column(name = "job_type", nullable = false, length = 50)
    private String jobType;

    @Column(name = "payload", length = Length.LONG32)
    private String payload;

    @Builder.Default
    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING";

    @Builder.Default
    @Column(name = "attempts", nullable = false)
    private Integer attempts = 0;

    @Column(name = "next_run_at")
    private LocalDateTime nextRunAt;

    @Column(name = "last_error", length = Length.LONG32)
    private String lastError;
}
