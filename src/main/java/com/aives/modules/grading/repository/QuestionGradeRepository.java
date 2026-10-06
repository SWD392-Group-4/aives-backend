package com.aives.modules.grading.repository;

import com.aives.modules.grading.entity.QuestionGrade;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface QuestionGradeRepository extends JpaRepository<QuestionGrade, String> {

    // Diem tung cau cua 1 luot thi theo thu tu cau hoi, lay kem cau hoi va rubric (deu LAZY)
    @Query("""
            select g from QuestionGrade g
            join fetch g.examQuestion eq
            join fetch eq.question q
            left join fetch q.rubric
            where g.attempt.id = :attemptId
            order by eq.orderNo asc
            """)
    List<QuestionGrade> findByAttemptInOrder(@Param("attemptId") String attemptId);

    @Query("""
            select g from QuestionGrade g
            join fetch g.attempt a
            join fetch a.exam
            join fetch g.examQuestion eq
            join fetch eq.question q
            left join fetch q.rubric
            where g.id = :id
            """)
    Optional<QuestionGrade> findWithAttemptById(@Param("id") String id);
}
