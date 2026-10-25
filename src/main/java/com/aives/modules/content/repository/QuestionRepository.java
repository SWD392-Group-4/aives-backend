package com.aives.modules.content.repository;

import com.aives.modules.content.entity.Question;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface QuestionRepository extends JpaRepository<Question, String>, JpaSpecificationExecutor<Question> {

    Page<Question> findByChapter_Id(String chapterId, Pageable pageable);

    int countByChapter_Id(String chapterId);
}