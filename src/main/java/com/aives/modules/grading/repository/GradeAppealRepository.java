package com.aives.modules.grading.repository;

import com.aives.modules.grading.entity.GradeAppeal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GradeAppealRepository extends JpaRepository<GradeAppeal, String> {

    boolean existsByQuestionGrade_IdAndStatus(String questionGradeId, String status);

    // Cac query duoi deu lay kem diem cau hoi, luot thi, phien thi va cau hoi (deu LAZY) trong 1 cau query

    @Query("""
            select ap from GradeAppeal ap
            join fetch ap.questionGrade g
            join fetch g.attempt a
            join fetch a.exam e
            join fetch g.examQuestion eq
            join fetch eq.question q
            left join fetch q.rubric
            where ap.id = :id
            """)
    Optional<GradeAppeal> findWithDetailsById(@Param("id") String id);

    // Don phuc khao cua moi phien thi, moi nhat truoc
    @Query("""
            select ap from GradeAppeal ap
            join fetch ap.questionGrade g
            join fetch g.attempt a
            join fetch a.exam e
            join fetch g.examQuestion eq
            join fetch eq.question q
            left join fetch q.rubric
            order by ap.createdAt desc, ap.id desc
            """)
    List<GradeAppeal> findAllWithDetails();

    // Don phuc khao cua cac phien thi do 1 giang vien tao
    @Query("""
            select ap from GradeAppeal ap
            join fetch ap.questionGrade g
            join fetch g.attempt a
            join fetch a.exam e
            join fetch g.examQuestion eq
            join fetch eq.question q
            left join fetch q.rubric
            where e.lecturerId = :lecturerId
            order by ap.createdAt desc, ap.id desc
            """)
    List<GradeAppeal> findByLecturer(@Param("lecturerId") String lecturerId);

    // Don phuc khao cua 1 sinh vien
    @Query("""
            select ap from GradeAppeal ap
            join fetch ap.questionGrade g
            join fetch g.attempt a
            join fetch a.exam e
            join fetch g.examQuestion eq
            join fetch eq.question q
            left join fetch q.rubric
            where a.studentId = :studentId
            order by ap.createdAt desc, ap.id desc
            """)
    List<GradeAppeal> findByStudent(@Param("studentId") String studentId);

    // Don phuc khao cua 1 luot thi, moi nhat truoc
    @Query("""
            select ap from GradeAppeal ap
            join fetch ap.questionGrade g
            where g.attempt.id = :attemptId
            order by ap.createdAt desc, ap.id desc
            """)
    List<GradeAppeal> findByAttempt(@Param("attemptId") String attemptId);

    @Query("select count(ap) from GradeAppeal ap where ap.status = 'PENDING'")
    long countPendingAll();

    @Query("select count(ap) from GradeAppeal ap join ap.questionGrade g join g.attempt a join a.exam e where e.lecturerId = :lecturerId and ap.status = 'PENDING'")
    long countPendingByLecturer(@Param("lecturerId") String lecturerId);

    @Query("select count(ap) from GradeAppeal ap join ap.questionGrade g join g.attempt a where a.studentId = :studentId and ap.status = 'PENDING'")
    long countPendingByStudentId(@Param("studentId") String studentId);
}
