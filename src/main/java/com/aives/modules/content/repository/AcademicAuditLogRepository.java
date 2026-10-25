package com.aives.modules.content.repository;

import com.aives.modules.content.document.AcademicAuditLog;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AcademicAuditLogRepository extends MongoRepository<AcademicAuditLog, String> {

    List<AcademicAuditLog> findByCourseIdOrderByCreatedAtDesc(String courseId);

    List<AcademicAuditLog> findByCourseIdAndChapterIdOrderByCreatedAtDesc(String courseId, String chapterId);

    List<AcademicAuditLog> findByTargetIdOrderByCreatedAtDesc(String targetId);

    List<AcademicAuditLog> findByActorIdOrderByCreatedAtDesc(String actorId);
}
