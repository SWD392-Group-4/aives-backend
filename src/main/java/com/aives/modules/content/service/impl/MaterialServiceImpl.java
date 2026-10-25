package com.aives.modules.content.service.impl;

import com.aives.common.exception.BusinessException;
import com.aives.common.exception.ErrorCode;
import com.aives.common.security.UserPrincipal;
import com.aives.common.util.AuditTime;
import com.aives.common.util.RandomIdGenerator;
import com.aives.modules.content.dto.MaterialResponse;
import com.aives.modules.content.entity.Chapter;
import com.aives.modules.content.entity.Material;
import com.aives.modules.content.repository.ChapterRepository;
import com.aives.modules.content.repository.MaterialRepository;
import com.aives.modules.content.service.GoogleCloudRagService;
import com.aives.modules.content.service.MaterialService;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class MaterialServiceImpl implements MaterialService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pptx", "ppt", "pdf", "docx");
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024L; // 50MB

    private final ChapterRepository chapterRepository;
    private final MaterialRepository materialRepository;
    private final GoogleCloudRagService googleCloudRagService;
    private final RandomIdGenerator idGenerator;

    @Override
    @Transactional(readOnly = true)
    public List<MaterialResponse> getMaterialsByChapterId(String chapterId) {
        if (!chapterRepository.existsById(chapterId)) {
            throw new BusinessException(ErrorCode.CHAPTER_NOT_FOUND, "Không tìm thấy chương: " + chapterId);
        }
        List<Material> materials = materialRepository.findByChapterIdOrderByCreatedAtDesc(chapterId);
        return materials.stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public MaterialResponse uploadMaterial(String chapterId, MultipartFile file, UserPrincipal principal) {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAPTER_NOT_FOUND, "Không tìm thấy chương: " + chapterId));

        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "File tải lên không được để trống");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "File quá lớn! Dung lượng tối đa là 50MB");
        }

        String originalFilename = file.getOriginalFilename();
        String ext = getFileExtension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessException(ErrorCode.MATERIAL_INVALID_TYPE,
                    "Định dạng file không hỗ trợ: chỉ chấp nhận .pptx, .ppt, .pdf, .docx");
        }

        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Không đọc được dữ liệu file tải lên");
        }

        String gcsUri = googleCloudRagService.uploadToGcs(originalFilename, fileBytes, file.getContentType());

        Material material = Material.builder()
                .id(idGenerator.next("MAT"))
                .chapterId(chapter.getId())
                .uploadedBy(principal.getId())
                .lecturerId(principal.getId())
                .title(originalFilename != null ? originalFilename : "Tài liệu học liệu")
                .fileName(originalFilename != null ? originalFilename : "slide." + ext)
                .fileSize(file.getSize())
                .fileType(ext)
                .gcsUri(gcsUri)
                .ragStatus("INDEXING")
                .chunkCount(0)
                .createdAt(LocalDateTime.now())
                .build();

        Material saved = materialRepository.save(material);
        log.info("Saved Material record: id={}, chapterId={}, gcsUri={}", saved.getId(), chapterId, gcsUri);

        googleCloudRagService.triggerRagImportAsync(saved.getId(), gcsUri);

        return toResponse(saved);
    }

    @Override
    @Transactional
    public MaterialResponse reindexMaterial(String materialId, UserPrincipal principal) {
        Material material = materialRepository.findById(materialId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MATERIAL_NOT_FOUND, "Không tìm thấy học liệu: " + materialId));

        material.setRagStatus("INDEXING");
        material.setErrorMessage(null);
        Material saved = materialRepository.save(material);

        googleCloudRagService.triggerRagImportAsync(saved.getId(), saved.getGcsUri());
        log.info("Triggered reindex for material: id={}", saved.getId());

        return toResponse(saved);
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    private MaterialResponse toResponse(Material m) {
        String fileUrl = m.getGcsUri();
        if (fileUrl != null && fileUrl.startsWith("gs://")) {
            fileUrl = "https://storage.googleapis.com/" + fileUrl.substring(5);
        }

        return MaterialResponse.builder()
                .id(m.getId())
                .chapterId(m.getChapterId())
                .lecturerId(m.getLecturerId())
                .lessonId(m.getLessonId())
                .topicId(m.getTopicId())
                .title(m.getTitle())
                .fileName(m.getFileName())
                .fileSize(m.getFileSize())
                .fileType(m.getFileType())
                .gcsUri(m.getGcsUri())
                .fileUrl(fileUrl)
                .ragCorpusId(m.getRagCorpusId())
                .ragStatus(m.getRagStatus())
                .chunkCount(m.getChunkCount())
                .errorMessage(m.getErrorMessage())
                .createdAt(AuditTime.toInstant(m.getCreatedAt()))
                .build();
    }
}
