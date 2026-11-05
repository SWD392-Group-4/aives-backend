package com.aives.modules.exam.repository;

import com.aives.modules.exam.entity.VivaExam;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface VivaExamRepository extends JpaRepository<VivaExam, String>, JpaSpecificationExecutor<VivaExam> {

    @Query("select count(e) from VivaExam e where e.cancelledAt is null and e.startAt <= :now and e.endAt > :now")
    long countOngoingSessions(@Param("now") LocalDateTime now);

    @Query("select count(e) from VivaExam e where e.lecturerId = :lecturerId and e.cancelledAt is null and e.startAt <= :now and e.endAt > :now")
    long countOngoingSessionsByLecturer(@Param("lecturerId") String lecturerId, @Param("now") LocalDateTime now);

    @Query("select count(e) from VivaExam e where e.cancelledAt is null and e.endAt > :now")
    long countUpcomingOrOngoingSessions(@Param("now") LocalDateTime now);

    @Query("select count(e) > 0 from VivaExam e where e.courseId = :courseId and e.cancelledAt is null and e.startAt <= :now")
    boolean hasStartedExamsForCourse(@Param("courseId") String courseId, @Param("now") LocalDateTime now);

    // Mon hoc da duoc dung cho phien thi nao chua (ke ca phien da huy) -> khong cho xoa mon
    boolean existsByCourseId(String courseId);
}

