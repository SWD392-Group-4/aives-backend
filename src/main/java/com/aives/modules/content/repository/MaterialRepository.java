package com.aives.modules.content.repository;

import com.aives.modules.content.entity.Material;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MaterialRepository extends JpaRepository<Material, String> {

    List<Material> findByChapter_IdOrderByCreatedAtDesc(String chapterId);

    List<Material> findByIdInAndChapter_Id(List<String> ids, String chapterId);

    int countByChapter_Id(String chapterId);
}