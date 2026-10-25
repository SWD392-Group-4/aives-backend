package com.aives.modules.content.document;

import com.aives.modules.content.enums.AuditAction;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "academic_audit_logs")
@CompoundIndexes({
        @CompoundIndex(name = "ix_course_created", def = "{'course_id': 1, 'created_at': -1}")
})
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

    @Indexed
    @Field("actor_id")
    private String actorId;

    @Field("actor_name")
    private String actorName;

    @Field("action")
    private AuditAction action;

    @Field("target_entity")
    private String targetEntity;

    @Indexed
    @Field("target_id")
    private String targetId;

    @Field("details")
    private Map<String, Object> details;

    @CreatedDate
    @Field("created_at")
    private LocalDateTime createdAt;
}
