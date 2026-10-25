package com.aives.modules.content.repository;

import com.aives.modules.content.entity.Material;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MaterialRepository extends JpaRepository<Material, String> {

    List<Material> findByChapter_IdOrderByCreatedAtDesc(String chapterId);

    List<Material> findByIdInAndChapter_Id(List<String> ids, String chapterId);

    List<Material> findByLecturerIdAndTopicId(String lecturerId, String topicId);

    List<Material> findByLecturerIdAndLessonIdAndTopicId(String lecturerId, String lessonId, String topicId);

    List<Material> findByLessonId(String lessonId);

    List<Material> findByTopicId(String topicId);

    List<Material> findByChapterIdOrderByCreatedAtDesc(String chapterId);

    List<Material> findByChapterId(String chapterId);

    long countByChapterId(String chapterId);

    @Query("SELECT m FROM Material m WHERE "
            + "(:lecturerId IS NULL OR m.lecturerId = :lecturerId) AND "
            + "(:lessonId IS NULL OR m.lessonId = :lessonId) AND "
            + "(:topicId IS NULL OR m.topicId = :topicId) "
            + "ORDER BY m.createdAt DESC")
    List<Material> findByFilters(
            @Param("lecturerId") String lecturerId,
            @Param("lessonId") String lessonId,
            @Param("topicId") String topicId);
}
