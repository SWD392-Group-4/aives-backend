package com.aives.modules.content.repository;

import com.aives.modules.content.entity.CourseLecturer;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CourseLecturerRepository extends JpaRepository<CourseLecturer, String> {
    List<CourseLecturer> findByCourse_Id(String courseId);
    List<CourseLecturer> findByUserId(String userId);
    Optional<CourseLecturer> findByCourse_IdAndUserId(String courseId, String userId);
    boolean existsByCourse_IdAndUserId(String courseId, String userId);
}
