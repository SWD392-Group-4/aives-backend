package com.aives.modules.content.repository;

import com.aives.modules.content.entity.Chapter;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChapterRepository extends JpaRepository<Chapter, String> {

    List<Chapter> findByCourse_IdOrderByOrderNoAsc(String courseId);

    int countByCourse_Id(String courseId);
}