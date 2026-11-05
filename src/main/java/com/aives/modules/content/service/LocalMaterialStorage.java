package com.aives.modules.content.service;

import com.aives.common.exception.BusinessException;
import com.aives.common.exception.ErrorCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Luu file hoc lieu tren o dia cua may chay backend (thay cho Google Cloud Storage).
 * Bat bang MATERIAL_STORAGE=local (mac dinh). Thu muc: MATERIAL_LOCAL_DIR (mac dinh uploads/materials).
 * Duong dan luu trong cot gcs_uri co dang "local:<ten file>".
 */
@Slf4j
@Component
public class LocalMaterialStorage {

    public static final String URI_PREFIX = "local:";

    private final String storageMode;
    private final Path baseDir;

    public LocalMaterialStorage(
            @Value("${app.materials.storage:local}") String storageMode,
            @Value("${app.materials.local-dir:uploads/materials}") String localDir) {
        this.storageMode = storageMode == null ? "local" : storageMode.trim().toLowerCase();
        this.baseDir = Path.of(localDir == null || localDir.isBlank() ? "uploads/materials" : localDir.trim())
                .toAbsolutePath().normalize();
    }

    /** true: luu local + doc chu bang PDFBox; false: dung Google Cloud Storage + Vertex AI RAG. */
    public boolean isLocalMode() {
        return !"gcs".equals(storageMode);
    }

    public static boolean isLocalUri(String uri) {
        return uri != null && uri.startsWith(URI_PREFIX);
    }

    public String save(String materialId, String ext, byte[] bytes) {
        String fileName = materialId + "." + ext;
        try {
            Files.createDirectories(baseDir);
            Files.write(baseDir.resolve(fileName), bytes);
            log.info("Saved material file locally: {}", baseDir.resolve(fileName));
            return URI_PREFIX + fileName;
        } catch (IOException ex) {
            log.error("Cannot save material file to {}", baseDir, ex);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Không lưu được file tài liệu lên máy chủ");
        }
    }

    public byte[] read(String uri) {
        Path file = resolve(uri);
        try {
            return Files.readAllBytes(file);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.MATERIAL_NOT_FOUND, "Không tìm thấy file tài liệu trên máy chủ: " + file.getFileName());
        }
    }

    public void delete(String uri) {
        try {
            Files.deleteIfExists(resolve(uri));
        } catch (IOException ex) {
            log.warn("Cannot delete local material file {}: {}", uri, ex.getMessage());
        }
    }

    private Path resolve(String uri) {
        String fileName = uri.substring(URI_PREFIX.length());
        Path file = baseDir.resolve(fileName).normalize();
        if (!file.startsWith(baseDir)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Đường dẫn tài liệu không hợp lệ");
        }
        return file;
    }
}
