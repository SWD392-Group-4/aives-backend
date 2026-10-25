package com.aives.modules.exam.repository;

import com.aives.modules.exam.entity.VivaExamQuestion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface VivaExamQuestionRepository extends JpaRepository<VivaExamQuestion, String> {

    long countByExam_Id(String examId);

    // Cau hoi cua phien theo thu tu hoi, lay kem noi dung cau hoi, rubric, chapter va course trong 1 cau query (deu la LAZY)
    @Query("""
            select eq from VivaExamQuestion eq
            join fetch eq.question q
            left join fetch q.rubric
            left join fetch q.chapter c
            left join fetch c.course
            where eq.exam.id = :examId
            order by eq.orderNo asc
            """)
    List<VivaExamQuestion> findWithQuestionByExamId(@Param("examId") String examId);

    @Modifying
    @Query("delete from VivaExamQuestion eq where eq.exam.id = :examId")
    void deleteByExamId(@Param("examId") String examId);
}
