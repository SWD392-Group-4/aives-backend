package com.aives.modules.exam.repository;

import com.aives.modules.exam.entity.VivaExam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface VivaExamRepository extends JpaRepository<VivaExam, String>, JpaSpecificationExecutor<VivaExam> {
}
