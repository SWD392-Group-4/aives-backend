package com.aives.modules.content.repository;

import com.aives.modules.content.entity.QuestionFigure;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionFigureRepository extends JpaRepository<QuestionFigure, String> {

    /** Chi lay id (khong tai anh) de biet cau hoi nao co hinh. */
    @Query("select f.id from QuestionFigure f where f.id in :questionIds")
    List<String> findQuestionIdsWithFigure(@Param("questionIds") Collection<String> questionIds);
}
