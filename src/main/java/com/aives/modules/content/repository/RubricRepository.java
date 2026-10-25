package com.aives.modules.content.repository;

import com.aives.modules.content.entity.Rubric;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RubricRepository extends JpaRepository<Rubric, String> {

    Optional<Rubric> findByCourse_Id(String courseId);

    boolean existsByCourse_Id(String courseId);
}