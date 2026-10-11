package com.aives.modules.content.repository;

import com.aives.modules.content.entity.Question;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface QuestionRepository extends JpaRepository<Question, String> {

    // Cac cau hoi theo trang thai, cu nhat truoc. Pageable dung de gioi han so cau lay ra.
    List<Question> findByStatusOrderByCreatedAtAscIdAsc(String status, Pageable pageable);

    // Cau hoi theo trang thai cua 1 giang vien: bo cau hoi (chu de) moi nhat truoc, trong 1 chu de thi theo thu tu soan
    @Query("""
            select q from Question q
            join q.topic t
            join t.lesson l
            where q.status = :status and l.lecturerId = :lecturerId
            order by t.createdAt desc, t.id desc, q.createdAt asc, q.id asc
            """)
    List<Question> findByOwnerAndStatus(@Param("lecturerId") String lecturerId,
                                        @Param("status") String status,
                                        Pageable pageable);

    // Cau hoi cua nhieu bai hoc, lay kem chu de, bai hoc va rubric (deu LAZY) trong 1 cau query
    @Query("""
            select q from Question q
            join fetch q.topic t
            join fetch t.lesson l
            left join fetch q.rubric
            where l.id in :lessonIds
            order by t.createdAt desc, t.id desc, q.createdAt asc, q.id asc
            """)
    List<Question> findWithTopicByLessonIds(@Param("lessonIds") Collection<String> lessonIds);

    @Query("""
            select q from Question q
            join fetch q.topic t
            join fetch t.lesson
            left join fetch q.rubric
            where q.id = :id
            """)
    Optional<Question> findWithTopicById(@Param("id") String id);

    // Cau hoi theo trang thai thuoc bai hoc co tieu de cho truoc (dung de cat du lieu mau cu)
    List<Question> findByTopic_Lesson_TitleAndStatus(String lessonTitle, String status);
}
