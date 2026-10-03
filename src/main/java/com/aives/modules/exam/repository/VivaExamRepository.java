package com.aives.modules.exam.repository;

import com.aives.modules.exam.entity.VivaExam;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface VivaExamRepository extends JpaRepository<VivaExam, String>, JpaSpecificationExecutor<VivaExam> {

    Optional<VivaExam> findByExamCode(String examCode);

    boolean existsByExamCode(String examCode);
}
