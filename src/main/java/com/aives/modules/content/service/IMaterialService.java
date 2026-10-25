package com.aives.modules.content.service;

import com.aives.modules.content.dto.MaterialResponse;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface IMaterialService {

    List<MaterialResponse> getMaterialsByChapterId(String chapterId);

    MaterialResponse uploadMaterial(String chapterId, MultipartFile file, String lecturerId);

    MaterialResponse triggerReindex(String materialId, String lecturerId);
}