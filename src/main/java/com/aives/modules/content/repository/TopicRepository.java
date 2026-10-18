package com.aives.modules.content.repository;

import com.aives.modules.content.entity.Topic;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TopicRepository extends JpaRepository<Topic, String> {

    // Chu de cua nhieu bai hoc, lay kem bai hoc (LAZY) trong 1 cau query
    @Query("""
            select t from Topic t
            join fetch t.lesson l
            where l.id in :lessonIds
            order by t.createdAt desc, t.id desc
            """)
    List<Topic> findWithLessonByLessonIds(@Param("lessonIds") Collection<String> lessonIds);

    @Query("""
            select t from Topic t
            join fetch t.lesson l
            where t.id = :id
            """)
    java.util.Optional<Topic> findWithLessonById(@Param("id") String id);

    boolean existsByLesson_Id(String lessonId);

    long countByLesson_Id(String lessonId);
}

