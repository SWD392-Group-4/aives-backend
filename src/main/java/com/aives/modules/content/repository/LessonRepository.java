package com.aives.modules.content.repository;

import com.aives.modules.content.entity.Lesson;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LessonRepository extends JpaRepository<Lesson, String> {

    boolean existsByTitle(String title);

    List<Lesson> findByLecturerIdOrderByCreatedAtDescIdDesc(String lecturerId);

    List<Lesson> findAllByOrderByCreatedAtDescIdDesc();
}
