package com.aives.modules.content.service.impl;

import com.aives.common.exception.BusinessException;
import com.aives.common.exception.ErrorCode;
import com.aives.common.util.RandomIdGenerator;
import com.aives.modules.auth.entity.User;
import com.aives.modules.auth.repository.UserRepository;
import com.aives.modules.content.dto.MaterialResponse;
import com.aives.modules.content.entity.Chapter;
import com.aives.modules.content.entity.Material;
import com.aives.modules.content.enums.MaterialRagStatus;
import com.aives.modules.content.repository.ChapterRepository;
import com.aives.modules.content.repository.MaterialRepository;
import com.aives.modules.content.service.IMaterialService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class MaterialServiceImpl implements IMaterialService {

    private final ChapterRepository chapterRepository;
    private final MaterialRepository materialRepository;
    private final UserRepository userRepository;
    private final RandomIdGenerator idGenerator;

    @Value("${app.rag.bucket-name:exam-slides-bucket-2026}")
    private String gcsBucketName;

    @Value("${app.rag.corpus-id:6917529027641081856}")
    private String ragCorpusId;

    @Override
    @Transactional(readOnly = true)
    public List<MaterialResponse> getMaterialsByChapterId(String chapterId) {
        log.info("Lay danh sach hoc lieu cua chuong: chapterId={}", chapterId);
        if (!chapterRepository.existsById(chapterId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy chương " + chapterId);
        }

        List<Material> materials = materialRepository.findByChapter_IdOrderByCreatedAtDesc(chapterId);
        return materials.stream().map(this::mapToResponse).toList();
    }

    @Override
    @Transactional
    public MaterialResponse uploadMaterial(String chapterId, MultipartFile file, String lecturerId) {
        log.info("Upload hoc lieu cho chuong: chapterId={}, file={}, lecturerId={}", chapterId, file.getOriginalFilename(), lecturerId);
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy chương " + chapterId));

        User lecturer = userRepository.findById(lecturerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy thông tin giảng viên"));

        // Validation file (BR-MAT-001): chi nhan .pdf, .pptx, .ppt dung luong <= 50MB
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Tệp tải lên không được để trống");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = StringUtils.getFilenameExtension(originalFilename);
        if (extension == null || (!extension.equalsIgnoreCase("pdf")
                && !extension.equalsIgnoreCase("pptx")
                && !extension.equalsIgnoreCase("ppt"))) {
            throw new BusinessException(ErrorCode.MATERIAL_INVALID_TYPE, "Định dạng file không hỗ trợ: chỉ chấp nhận .pdf, .pptx, .ppt");
        }

        if (file.getSize() > 50 * 1024 * 1024L) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Dung lượng tệp vượt quá giới hạn 50MB");
        }

        String materialId = idGenerator.next("MA");
        String courseId = chapter.getCourse().getId();
        String gcsKey = String.format("courses/%s/chapters/%s/%s_%s", courseId, chapterId, materialId, originalFilename);
        String gcsUrl = String.format("gs://%s/%s", gcsBucketName, gcsKey);

        // Luu metadata vao DB o trang thai INDEXING
        Material material = Material.builder()
                .chapter(chapter)
                .uploadedBy(lecturer)
                .title(originalFilename)
                .fileUrl(gcsUrl)
                .fileType(extension.toLowerCase())
                .fileSize(file.getSize())
                .ragStatus(MaterialRagStatus.INDEXING)
                .ragCorpusId(ragCorpusId)
                .chunkCount(0)
                .build();
        material.setId(materialId);

        material = materialRepository.save(material);

        // Kich hoat tien trinh doc va lap chi muc RAG bat dong bo
        processRagIndexingAsync(material.getId());

        return mapToResponse(material);
    }

    @Override
    @Transactional
    public MaterialResponse triggerReindex(String materialId, String lecturerId) {
        log.info("Kich hoat lap chi muc lai cho hoc lieu: materialId={}, lecturerId={}", materialId, lecturerId);
        Material material = materialRepository.findById(materialId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MATERIAL_NOT_FOUND, "Không tìm thấy tài liệu " + materialId));

        material.setRagStatus(MaterialRagStatus.INDEXING);
        material.setErrorMessage(null);
        material = materialRepository.save(material);

        processRagIndexingAsync(material.getId());
        return mapToResponse(material);
    }

    @Async
    public void processRagIndexingAsync(String materialId) {
        try {
            log.info("Bat dau lap chi muc RAG cho hoc lieu: materialId={}", materialId);
            // Mo phong qua trinh chunking va embedding tren Vertex AI RAG Engine
            Thread.sleep(3000);

            Material material = materialRepository.findById(materialId).orElse(null);
            if (material != null) {
                material.setRagStatus(MaterialRagStatus.READY);
                material.setChunkCount(15);
                materialRepository.save(material);
                log.info("Lap chi muc RAG thanh cong: materialId={}, status=READY", materialId);
            }
        } catch (Exception ex) {
            log.error("Loi khi lap chi muc RAG cho hoc lieu: materialId={}, err={}", materialId, ex.getMessage(), ex);
            Material material = materialRepository.findById(materialId).orElse(null);
            if (material != null) {
                material.setRagStatus(MaterialRagStatus.FAILED);
                material.setErrorMessage(ex.getMessage());
                materialRepository.save(material);
            }
        }
    }

    private MaterialResponse mapToResponse(Material material) {
        return MaterialResponse.builder()
                .id(material.getId())
                .chapterId(material.getChapter() != null ? material.getChapter().getId() : null)
                .title(material.getTitle())
                .fileUrl(material.getFileUrl())
                .fileType(material.getFileType())
                .fileSize(material.getFileSize())
                .ragStatus(material.getRagStatus())
                .createdAt(material.getCreatedAt())
                .build();
    }
}