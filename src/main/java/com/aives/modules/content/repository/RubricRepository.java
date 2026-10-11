package com.aives.modules.content.repository;

import com.aives.modules.content.entity.Rubric;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RubricRepository extends JpaRepository<Rubric, String> {

    List<Rubric> findByCreatedByOrderByCreatedAtDescIdDesc(String createdBy);

    List<Rubric> findAllByOrderByCreatedAtDescIdDesc();
}
