package com.aives.modules.content.service;

import com.aives.common.security.UserPrincipal;
import com.aives.modules.content.dto.MaterialResponse;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface MaterialService {

    List<MaterialResponse> getMaterialsByChapterId(String chapterId);

    MaterialResponse uploadMaterial(String chapterId, MultipartFile file, UserPrincipal principal);

    MaterialResponse reindexMaterial(String materialId, UserPrincipal principal);
}
