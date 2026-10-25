package com.aives.modules.content.document;

import java.time.Instant;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "academic_audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AcademicAuditLog {

    @Id
    private String id;

    @Indexed
    @Field("course_id")
    private String courseId;

    @Field("chapter_id")
    private String chapterId;

    @Field("actor_id")
    private String actorId;

    @Field("actor_name")
    private String actorName;

    @Field("action")
    private String action;

    @Field("target_entity")
    private String targetEntity;

    @Field("target_id")
    private String targetId;

    @Field("details")
    private Map<String, Object> details;

    @Field("created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();
}
