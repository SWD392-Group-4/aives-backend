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
import com.aives.modules.content.service.LocalMaterialStorage;
import com.aives.modules.content.service.MaterialService;
import com.aives.modules.content.util.MaterialTextExtractor;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Hoc lieu cua chuong. 2 che do (MATERIAL_STORAGE):
 *  - local (mac dinh): luu file tren may chay backend, doc chu bang PDFBox, READY ngay. Khong can Google Cloud.
 *  - gcs: day len Google Cloud Storage va nap vao Vertex AI RAG Corpus (can rag-key.json).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MaterialServiceImpl implements MaterialService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pptx", "ppt", "pdf", "docx");
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024L; // 50MB
    private static final int CHARS_PER_CHUNK = 1000;

    private final ChapterRepository chapterRepository;
    private final MaterialRepository materialRepository;
    private final GoogleCloudRagService googleCloudRagService;
    private final LocalMaterialStorage localStorage;
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
        boolean local = localStorage.isLocalMode();
        if (local && !MaterialTextExtractor.isSupported(ext)) {
            throw new BusinessException(ErrorCode.MATERIAL_INVALID_TYPE,
                    "File .ppt (định dạng cũ) chưa đọc được nội dung. Hãy lưu lại thành .pptx hoặc .pdf rồi tải lên");
        }

        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Không đọc được dữ liệu file tải lên");
        }

        String materialId = idGenerator.next("MAT");
        String storageUri = local
                ? localStorage.save(materialId, ext, fileBytes)
                : googleCloudRagService.uploadToGcs(originalFilename, fileBytes, file.getContentType());

        Material material = Material.builder()
                .id(materialId)
                .chapterId(chapter.getId())
                .uploadedBy(principal.getId())
                .lecturerId(principal.getId())
                .title(originalFilename != null ? originalFilename : "Tài liệu học liệu")
                .fileName(originalFilename != null ? originalFilename : "slide." + ext)
                .fileSize(file.getSize())
                .fileType(ext)
                .gcsUri(storageUri)
                .ragCorpusId(local ? "LOCAL" : null) // null -> @PrePersist gan corpus Vertex AI mac dinh
                .ragStatus("INDEXING")
                .chunkCount(0)
                .createdAt(LocalDateTime.now())
                .build();

        if (local) {
            applyExtractedText(material, fileBytes, ext);
        }

        Material saved = materialRepository.save(material);
        log.info("Saved Material record: id={}, chapterId={}, uri={}, status={}",
                saved.getId(), chapterId, storageUri, saved.getRagStatus());

        if (!local) {
            googleCloudRagService.triggerRagImportAsync(saved.getId(), storageUri);
        }

        return toResponse(saved);
    }

    @Override
    @Transactional
    public MaterialResponse reindexMaterial(String materialId, UserPrincipal principal) {
        Material material = materialRepository.findById(materialId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MATERIAL_NOT_FOUND, "Không tìm thấy học liệu: " + materialId));

        if (LocalMaterialStorage.isLocalUri(material.getGcsUri())) {
            // File luu local: doc lai chu tu file tren o dia
            byte[] bytes = localStorage.read(material.getGcsUri());
            applyExtractedText(material, bytes, material.getFileType());
            Material saved = materialRepository.save(material);
            log.info("Re-extracted text for local material: id={}, status={}", saved.getId(), saved.getRagStatus());
            return toResponse(saved);
        }

        material.setRagStatus("INDEXING");
        material.setErrorMessage(null);
        Material saved = materialRepository.save(material);

        googleCloudRagService.triggerRagImportAsync(saved.getId(), saved.getGcsUri());
        log.info("Triggered reindex for material: id={}", saved.getId());

        return toResponse(saved);
    }

    @Override
    @Transactional
    public void deleteMaterial(String materialId, UserPrincipal principal) {
        Material material = materialRepository.findById(materialId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MATERIAL_NOT_FOUND, "Không tìm thấy học liệu: " + materialId));
        String uri = material.getGcsUri();
        materialRepository.delete(material);
        if (LocalMaterialStorage.isLocalUri(uri)) {
            localStorage.delete(uri);
        } else if (uri != null && uri.startsWith("gs://")) {
            try {
                googleCloudRagService.deleteFromGcs(uri);
            } catch (RuntimeException ex) {
                log.warn("Cannot delete GCS object {}: {}", uri, ex.getMessage());
            }
        }
        log.info("Deleted material: id={}, by={}", materialId, principal != null ? principal.getId() : null);
    }

    /** Doc chu tu file: co chu -> READY, khong co chu (vd PDF scan anh) -> FAILED kem ly do. */
    private void applyExtractedText(Material material, byte[] bytes, String ext) {
        String text;
        try {
            text = MaterialTextExtractor.extract(bytes, ext);
        } catch (IOException | RuntimeException ex) {
            log.warn("Cannot extract text from material {}: {}", material.getId(), ex.getMessage());
            text = "";
        }

        if (text.isBlank()) {
            material.setContentText(null);
            material.setRagStatus("FAILED");
            material.setChunkCount(0);
            material.setErrorMessage("Không đọc được chữ trong file (file scan dạng ảnh hoặc bị lỗi). "
                    + "Hãy dùng file PDF/PPTX có chữ chọn được.");
            return;
        }

        material.setContentText(text);
        material.setRagStatus("READY");
        material.setChunkCount((text.length() + CHARS_PER_CHUNK - 1) / CHARS_PER_CHUNK);
        material.setErrorMessage(null);
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
        } else if (LocalMaterialStorage.isLocalUri(fileUrl)) {
            fileUrl = null; // file local khong co link cong khai
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
