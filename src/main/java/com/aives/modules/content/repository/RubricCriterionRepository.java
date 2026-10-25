package com.aives.modules.content.repository;

import com.aives.modules.content.entity.RubricCriterion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RubricCriterionRepository extends JpaRepository<RubricCriterion, String> {

    List<RubricCriterion> findByRubric_IdOrderByOrderNoAsc(String rubricId);

    void deleteByRubric_Id(String rubricId);
}