package com.aives.modules.content.repository;

import com.aives.modules.content.entity.RubricCriterion;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RubricCriterionRepository extends JpaRepository<RubricCriterion, String> {

    List<RubricCriterion> findByRubric_IdOrderByOrderNoAsc(String rubricId);

    List<RubricCriterion> findByRubric_IdInOrderByOrderNoAsc(Collection<String> rubricIds);

    void deleteByRubric_Id(String rubricId);
}
