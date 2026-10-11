package com.aives.modules.grading.repository;

import com.aives.modules.grading.entity.CriteriaGrade;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CriteriaGradeRepository extends JpaRepository<CriteriaGrade, String> {

    @Query("""
            select c from CriteriaGrade c
            join fetch c.rubricCriterion rc
            where c.questionGrade.id in :questionGradeIds
            order by rc.orderNo asc
            """)
    List<CriteriaGrade> findByQuestionGradeIds(@Param("questionGradeIds") Collection<String> questionGradeIds);
}
