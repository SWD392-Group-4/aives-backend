package com.aives.modules.exam.repository;

import com.aives.modules.exam.entity.VivaAttempt;
import com.aives.modules.exam.enums.AttemptStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface VivaAttemptRepository extends JpaRepository<VivaAttempt, String> {

    // Luot thi moi nhat cua sinh vien trong phien (attempt_no lon nhat)
    Optional<VivaAttempt> findFirstByExam_IdAndStudentIdOrderByAttemptNoDesc(String examId, String studentId);

    long countByExam_Id(String examId);

    List<VivaAttempt> findByExam_IdOrderByStartedAtDesc(String examId);

    // Lay kem phien thi trong 1 cau query (exam la LAZY)
    @Query("select a from VivaAttempt a join fetch a.exam where a.id = :id")
    Optional<VivaAttempt> findWithExamById(@Param("id") String id);

    // Dem so luot thi cua nhieu phien cung luc: moi dong la [examId, soLuot]
    @Query("select a.exam.id, count(a) from VivaAttempt a where a.exam.id in :examIds group by a.exam.id")
    List<Object[]> countGroupedByExamIds(@Param("examIds") Collection<String> examIds);

    long countByStudentIdAndStatus(String studentId, AttemptStatus status);

    @Query("select avg(a.totalFinalScore) from VivaAttempt a where a.studentId = :studentId and a.resultStatus = 'PUBLISHED' and a.totalFinalScore is not null")
    Double getAveragePublishedScoreByStudentId(@Param("studentId") String studentId);

    @Query("select count(a) from VivaAttempt a where a.resultStatus = 'PENDING_REVIEW'")
    long countPendingReviewAll();

    @Query("select count(a) from VivaAttempt a join a.exam e where e.lecturerId = :lecturerId and a.resultStatus = 'PENDING_REVIEW'")
    long countPendingReviewByLecturer(@Param("lecturerId") String lecturerId);
}

