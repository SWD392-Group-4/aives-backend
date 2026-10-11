package com.aives.modules.grading.repository;

import com.aives.modules.exam.entity.VivaAttempt;
import com.aives.modules.exam.enums.AttemptStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Cac cau truy van luot thi phuc vu cham diem va tham dinh (bang viva_attempts thuoc module exam). */
public interface GradingAttemptRepository extends Repository<VivaAttempt, String> {

    /**
     * Doi result_status CHI KHI dang o trang thai mong doi. Tra ve so dong da sua:
     * 0 nghia la da co luong khac xu ly luot thi nay truoc, luong hien tai phai dung lai.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update VivaAttempt a set a.resultStatus = :to, a.updatedAt = :now
            where a.id = :id and a.resultStatus = :from
            """)
    int changeResultStatus(@Param("id") String id, @Param("from") String from, @Param("to") String to,
                           @Param("now") LocalDateTime now);

    // Luot thi da xong, chua cham va co it nhat 1 luot hoi - dap (luot thi chua bat dau phong van thi khong cham)
    @Query("""
            select a.id from VivaAttempt a
            where a.status = :completed and a.resultStatus = 'NONE'
              and exists (select 1 from InterviewExchange e where e.attempt = a)
            order by a.completedAt asc
            """)
    List<String> findIdsWaitingForGrading(@Param("completed") AttemptStatus completed, Pageable pageable);

    // Luot thi da qua han ma chua duoc chot (sinh vien tat trinh duyet giua chung)
    @Query("select a from VivaAttempt a where a.status = :inProgress and a.deadlineAt <= :now")
    List<VivaAttempt> findOverdue(@Param("inProgress") AttemptStatus inProgress, @Param("now") LocalDateTime now);

    // Cac luot thi da co ket qua cham (dang cham, cho tham dinh, cham loi, da cong bo) cua moi phien thi
    @Query("""
            select a from VivaAttempt a
            join fetch a.exam e
            where a.status = :completed and a.resultStatus <> 'NONE'
            order by a.completedAt desc, a.id desc
            """)
    List<VivaAttempt> findForReview(@Param("completed") AttemptStatus completed);

    // Nhu tren nhung chi cac phien thi do 1 giang vien tao
    @Query("""
            select a from VivaAttempt a
            join fetch a.exam e
            where a.status = :completed and a.resultStatus <> 'NONE' and e.lecturerId = :lecturerId
            order by a.completedAt desc, a.id desc
            """)
    List<VivaAttempt> findForReviewByLecturer(@Param("completed") AttemptStatus completed,
                                              @Param("lecturerId") String lecturerId);

    // Luot thi da xong cua 1 sinh vien. Bo qua luot thi chua co luot hoi - dap nao (vao phong roi thoat,
    // hoac luot thi tu truoc khi co phong van AI) vi khong co gi de cham hay xem.
    @Query("""
            select a from VivaAttempt a
            join fetch a.exam
            where a.studentId = :studentId and a.status = :completed
              and exists (select 1 from InterviewExchange e where e.attempt = a)
            order by a.completedAt desc, a.id desc
            """)
    List<VivaAttempt> findCompletedOfStudent(@Param("studentId") String studentId,
                                             @Param("completed") AttemptStatus completed);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update VivaAttempt a set a.resultStatus = 'NONE', a.updatedAt = :now where a.resultStatus = 'GRADING'")
    int resetInterruptedGrading(@Param("now") LocalDateTime now);
}
