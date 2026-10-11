package com.aives.modules.vivaroom.repository;

import com.aives.modules.vivaroom.entity.InterviewExchange;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InterviewExchangeRepository extends JpaRepository<InterviewExchange, String> {

    // Moi luot hoi - dap cua 1 luot thi theo dung thu tu da hoi: cau goc (depth 0) roi den cac cau hoi xoay
    @Query("""
            select e from InterviewExchange e
            join fetch e.examQuestion eq
            where e.attempt.id = :attemptId
            order by eq.orderNo asc, e.depth asc
            """)
    List<InterviewExchange> findByAttemptInOrder(@Param("attemptId") String attemptId);

    /**
     * Ghi cau tra loi, CHI KHI luot nay chua co cau tra loi. Tra ve so dong da sua:
     * 0 nghia la da co request khac tra loi truoc (bam 2 lan, mo 2 tab), request nay phai dung lai.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update InterviewExchange e
            set e.transcript = :transcript, e.answerEndedAt = :answeredAt, e.updatedAt = :answeredAt
            where e.id = :id and e.transcript is null and e.aiDecision is null
            """)
    int saveAnswerIfUnanswered(@Param("id") String id,
                               @Param("transcript") String transcript,
                               @Param("answeredAt") LocalDateTime answeredAt);
}
