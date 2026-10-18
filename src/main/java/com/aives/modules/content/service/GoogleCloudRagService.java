package com.aives.modules.content.service;

import com.aives.common.exception.BusinessException;
import com.aives.common.exception.ErrorCode;
import com.aives.common.security.UserPrincipal;
import com.aives.common.util.AuditTime;
import com.aives.common.util.RandomIdGenerator;
import com.aives.integration.ai.LlmClient;
import com.aives.modules.auth.enums.RoleName;
import com.aives.modules.content.dto.GenerateQuestionsFromRagRequest;
import com.aives.modules.content.dto.MaterialResponse;
import com.aives.modules.content.dto.QuestionResponse;
import com.aives.modules.content.entity.Lesson;
import com.aives.modules.content.entity.Material;
import com.aives.modules.content.entity.Question;
import com.aives.modules.content.entity.Rubric;
import com.aives.modules.content.entity.RubricCriterion;
import com.aives.modules.content.entity.Topic;
import com.aives.modules.content.mapper.ContentMapper;
import com.aives.modules.content.repository.LessonRepository;
import com.aives.modules.content.repository.MaterialRepository;
import com.aives.modules.content.repository.QuestionRepository;
import com.aives.modules.content.repository.RubricCriterionRepository;
import com.aives.modules.content.repository.RubricRepository;
import com.aives.modules.content.repository.TopicRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleCloudRagService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pptx", "ppt", "pdf", "docx");
    private static final Set<String> VALID_BLOOM_LEVELS = Set.of("REMEMBER", "UNDERSTAND", "APPLY", "ANALYZE");
    private static final BigDecimal FULL_WEIGHT = new BigDecimal("100.00");

    @Value("${app.rag.project-id:precise-ray-512018-v9}")
    private String projectId;

    @Value("${app.rag.location:asia-southeast1}")
    private String location;

    @Value("${app.rag.bucket-name:exam-slides-bucket-2026}")
    private String bucketName;

    @Value("${app.rag.corpus-id:6917529027641081856}")
    private String corpusId;

    @Value("${app.rag.credentials-path:}")
    private String configuredCredentialsPath;

    private final MaterialRepository materialRepository;
    private final LessonRepository lessonRepository;
    private final TopicRepository topicRepository;
    private final RubricRepository rubricRepository;
    private final RubricCriterionRepository criterionRepository;
    private final QuestionRepository questionRepository;
    private final ContentMapper contentMapper;
    private final RandomIdGenerator idGenerator;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private Storage storageClient;
    private GoogleCredentials cachedCredentials;

    // ==================== CREDENTIALS & GCS STORAGE ====================

    private synchronized GoogleCredentials getGoogleCredentials() {
        if (cachedCredentials != null) {
            return cachedCredentials;
        }

        List<Path> candidatePaths = new ArrayList<>();
        if (configuredCredentialsPath != null && !configuredCredentialsPath.isBlank()) {
            candidatePaths.add(Path.of(configuredCredentialsPath.trim()));
        }
        String envCreds = System.getenv("GOOGLE_RAG_CREDENTIALS");
        if (envCreds != null && !envCreds.isBlank()) {
            candidatePaths.add(Path.of(envCreds.trim()));
        }
        String appCreds = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
        if (appCreds != null && !appCreds.isBlank()) {
            candidatePaths.add(Path.of(appCreds.trim()));
        }

        candidatePaths.add(Path.of("../rag-key.json"));
        candidatePaths.add(Path.of("rag-key.json"));
        candidatePaths.add(Path.of("c:/Edisk/dow/Tai_lieu/ky7/swd/aives/rag-key.json"));

        Path foundPath = null;
        for (Path p : candidatePaths) {
            if (p != null && Files.exists(p)) {
                foundPath = p.toAbsolutePath().normalize();
                break;
            }
        }

        if (foundPath == null) {
            log.warn("Không tìm thấy file credentials Google RAG trong các đường dẫn chỉ định");
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Chưa cấu hình Google Cloud credentials (rag-key.json)");
        }

        try (InputStream in = new FileInputStream(foundPath.toFile())) {
            cachedCredentials = GoogleCredentials.fromStream(in)
                    .createScoped(Collections.singletonList("https://www.googleapis.com/auth/cloud-platform"));
            log.info("Google Cloud RAG credentials loaded successfully from: {}", foundPath);
            return cachedCredentials;
        } catch (IOException ex) {
            log.error("Lỗi đọc file credentials Google Cloud: {}", foundPath, ex);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Không đọc được file credentials Google Cloud");
        }
    }

    private synchronized Storage getStorage() {
        if (storageClient == null) {
            storageClient = StorageOptions.newBuilder()
                    .setProjectId(projectId)
                    .setCredentials(getGoogleCredentials())
                    .build()
                    .getService();
        }
        return storageClient;
    }

    private String getAccessToken() {
        try {
            GoogleCredentials credentials = getGoogleCredentials();
            credentials.refreshIfExpired();
            AccessToken token = credentials.getAccessToken();
            if (token == null) {
                credentials.refresh();
                token = credentials.getAccessToken();
            }
            return token.getTokenValue();
        } catch (Exception ex) {
            log.error("Không lấy được OAuth2 access token từ Service Account", ex);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Lỗi xác thực Google Cloud: " + ex.getMessage());
        }
    }

    public String uploadToGcs(String originalFilename, byte[] fileBytes, String contentType) {
        String ext = getFileExtension(originalFilename);
        String objectName = "slides/" + UUID.randomUUID() + (ext.isEmpty() ? "" : "." + ext);
        BlobId blobId = BlobId.of(bucketName, objectName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                .setContentType(contentType != null && !contentType.isBlank() ? contentType : "application/octet-stream")
                .build();
        getStorage().create(blobInfo, fileBytes);
        String gcsUri = "gs://" + bucketName + "/" + objectName;
        log.info("Uploaded material to GCS: {}", gcsUri);
        return gcsUri;
    }

    public void deleteFromGcs(String gcsUri) {
        if (gcsUri == null || !gcsUri.startsWith("gs://")) {
            return;
        }
        try {
            String path = gcsUri.substring(5);
            int slashIndex = path.indexOf('/');
            if (slashIndex > 0) {
                String bucket = path.substring(0, slashIndex);
                String objectName = path.substring(slashIndex + 1);
                getStorage().delete(BlobId.of(bucket, objectName));
                log.info("Deleted material from GCS: {}", gcsUri);
            }
        } catch (Exception ex) {
            log.warn("Không xóa được file trên GCS: {}", gcsUri, ex);
        }
    }

    // ==================== VERTEX AI RAG IMPORT ====================

    public void triggerRagImportAsync(String materialId, String gcsUri) {
        CompletableFuture.runAsync(() -> {
            try {
                String token = getAccessToken();
                String importUrl = String.format(
                        "https://%s-aiplatform.googleapis.com/v1beta1/projects/%s/locations/%s/ragCorpora/%s/ragFiles:import",
                        location, projectId, location, corpusId);

                Map<String, Object> body = Map.of(
                        "importRagFilesConfig", Map.of(
                                "gcsSource", Map.of(
                                        "uris", List.of(gcsUri)
                                ),
                                "ragFileChunkingConfig", Map.of(
                                        "fixedLengthChunking", Map.of(
                                                "chunkSize", 1024,
                                                "chunkOverlap", 256
                                        )
                                )
                        )
                );

                String payload = objectMapper.writeValueAsString(body);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(importUrl))
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(30))
                        .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                        .build();

                log.info("Triggering Vertex AI RAG import for material: {} with URI: {}", materialId, gcsUri);
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    JsonNode respNode = objectMapper.readTree(response.body());
                    String opName = respNode.has("name") ? respNode.get("name").asText() : null;
                    log.info("RAG import initiated for material {}. Operation: {}", materialId, opName);

                    // Poll operation for a short while
                    boolean completed = false;
                    if (opName != null) {
                        completed = pollOperationUntilDone(materialId, opName, token);
                    }

                    if (!completed) {
                        // Mark as READY so user can test question generation
                        updateMaterialRagStatus(materialId, "READY", 1, null);
                    }
                } else {
                    log.error("Vertex AI RAG import failed for {}: HTTP {} - {}", materialId, response.statusCode(), response.body());
                    updateMaterialRagStatus(materialId, "FAILED", 0, "Vertex AI Import HTTP " + response.statusCode() + ": " + response.body());
                }
            } catch (Exception ex) {
                log.error("Lỗi trong quá trình kích hoạt RAG import cho material {}", materialId, ex);
                updateMaterialRagStatus(materialId, "FAILED", 0, ex.getMessage());
            }
        });
    }

    private boolean pollOperationUntilDone(String materialId, String opName, String token) {
        String opUrl = "https://" + location + "-aiplatform.googleapis.com/v1beta1/" + opName;
        for (int i = 0; i < 15; i++) {
            try {
                Thread.sleep(2000);
                HttpRequest opReq = HttpRequest.newBuilder()
                        .uri(URI.create(opUrl))
                        .header("Authorization", "Bearer " + token)
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();
                HttpResponse<String> opResp = httpClient.send(opReq, HttpResponse.BodyHandlers.ofString());
                if (opResp.statusCode() == 200) {
                    JsonNode opNode = objectMapper.readTree(opResp.body());
                    if (opNode.has("done") && opNode.get("done").asBoolean()) {
                        if (opNode.has("error")) {
                            String errMsg = opNode.get("error").get("message").asText();
                            log.error("RAG import operation failed: {}", errMsg);
                            updateMaterialRagStatus(materialId, "FAILED", 0, errMsg);
                        } else {
                            int chunks = 1;
                            if (opNode.has("response") && opNode.get("response").has("importedRagFilesCount")) {
                                chunks = opNode.get("response").get("importedRagFilesCount").asInt(1);
                            }
                            log.info("RAG import completed successfully for material {}", materialId);
                            updateMaterialRagStatus(materialId, "READY", chunks, null);
                        }
                        return true;
                    }
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return false;
            } catch (Exception e) {
                log.warn("Polling error for operation {}: {}", opName, e.getMessage());
            }
        }
        return false;
    }

    @Transactional
    public void updateMaterialRagStatus(String materialId, String status, int chunkCount, String errorMessage) {
        materialRepository.findById(materialId).ifPresent(material -> {
            material.setRagStatus(status);
            material.setChunkCount(chunkCount);
            material.setErrorMessage(errorMessage);
            materialRepository.save(material);
            log.info("Updated material {} RAG status to {}", materialId, status);
        });
    }

    // ==================== MATERIAL CRUD OPERATIONS ====================

    @Transactional
    public MaterialResponse uploadMaterial(
            UserPrincipal actor,
            MultipartFile file,
            String title,
            String lessonId,
            String topicId) {

        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "File không được để trống");
        }

        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "document";
        String ext = getFileExtension(originalFilename).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessException(ErrorCode.MATERIAL_INVALID_TYPE);
        }

        if (lessonId != null && !lessonId.isBlank()) {
            lessonRepository.findById(lessonId.trim()).orElseThrow(
                    () -> new BusinessException(ErrorCode.LESSON_NOT_FOUND));
        }
        if (topicId != null && !topicId.isBlank()) {
            topicRepository.findById(topicId.trim()).orElseThrow(
                    () -> new BusinessException(ErrorCode.TOPIC_NOT_FOUND));
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.IMPORT_FILE_INVALID, "Không đọc được file gửi lên");
        }

        String gcsUri = uploadToGcs(originalFilename, bytes, file.getContentType());
        String materialTitle = (title != null && !title.isBlank()) ? title.trim() : originalFilename;

        Material material = Material.builder()
                .id(idGenerator.next("MAT_"))
                .lecturerId(actor.getId())
                .lessonId(lessonId != null && !lessonId.isBlank() ? lessonId.trim() : null)
                .topicId(topicId != null && !topicId.isBlank() ? topicId.trim() : null)
                .title(materialTitle)
                .fileName(originalFilename)
                .fileSize(file.getSize())
                .fileType(ext)
                .gcsUri(gcsUri)
                .ragCorpusId(corpusId)
                .ragStatus("INDEXING")
                .chunkCount(0)
                .createdAt(LocalDateTime.now())
                .build();

        Material saved = materialRepository.save(material);

        // Kích hoạt import RAG bất đồng bộ
        triggerRagImportAsync(saved.getId(), gcsUri);

        return toMaterialResponse(saved);
    }

    @Transactional
    public List<MaterialResponse> syncMaterialsFromRagCorpus(UserPrincipal actor) {
        try {
            String token = getAccessToken();
            String listUrl = String.format(
                    "https://%s-aiplatform.googleapis.com/v1beta1/projects/%s/locations/%s/ragCorpora/%s/ragFiles",
                    location, projectId, location, corpusId);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(listUrl))
                    .header("Authorization", "Bearer " + token)
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode ragFiles = root.path("ragFiles");
                if (ragFiles.isArray()) {
                    for (JsonNode rf : ragFiles) {
                        String name = rf.path("name").asText(""); // projects/.../ragFiles/5811970578693285086
                        String displayName = rf.path("displayName").asText("").trim();
                        if (displayName.isBlank() && !name.isBlank()) {
                            displayName = name.substring(name.lastIndexOf('/') + 1);
                        }
                        String state = rf.path("fileStatus").path("state").asText("ACTIVE");
                        String status = "ACTIVE".equalsIgnoreCase(state) ? "READY" : state;

                        final String finalFileName = displayName;
                        boolean exists = materialRepository.findAll().stream()
                                .anyMatch(m -> finalFileName.equalsIgnoreCase(m.getFileName()) || finalFileName.equalsIgnoreCase(m.getTitle()));

                        if (!exists) {
                            String ext = getFileExtension(displayName).toLowerCase(Locale.ROOT);
                            if (ext.isBlank()) {
                                ext = "pptx";
                            }
                            Material syncedMaterial = Material.builder()
                                    .id(idGenerator.next("MAT_"))
                                    .lecturerId(actor.getId())
                                    .title(displayName)
                                    .fileName(displayName)
                                    .fileSize(1024L * 1024L) // default 1MB
                                    .fileType(ext)
                                    .gcsUri("gs://" + bucketName + "/" + displayName)
                                    .ragCorpusId(corpusId)
                                    .ragStatus(status)
                                    .chunkCount(1)
                                    .createdAt(LocalDateTime.now())
                                    .build();
                            materialRepository.save(syncedMaterial);
                            log.info("Synced RAG file from Cloud Corpus into database: {}", displayName);
                        }
                    }
                }
            } else {
                log.warn("Failed to fetch ragFiles from Vertex AI Corpus. HTTP {}: {}", response.statusCode(), response.body());
            }
        } catch (Exception ex) {
            log.error("Lỗi khi đồng bộ RAG Files từ Vertex AI Corpus", ex);
        }

        return listMaterials(actor, null, null);
    }

    @Transactional
    public List<MaterialResponse> listMaterials(UserPrincipal actor, String lessonId, String topicId) {
        String filterLecturer = (actor.getRole() == RoleName.ADMIN) ? null : actor.getId();
        String filterLesson = (lessonId != null && !lessonId.isBlank()) ? lessonId.trim() : null;
        String filterTopic = (topicId != null && !topicId.isBlank()) ? topicId.trim() : null;

        List<Material> list = materialRepository.findByFilters(filterLecturer, filterLesson, filterTopic);
        // Nếu DB chưa có học liệu nào của giảng viên này, tự động đồng bộ từ RAG Corpus về
        if (list.isEmpty() && (filterLesson == null && filterTopic == null)) {
            syncMaterialsFromRagCorpus(actor);
            list = materialRepository.findByFilters(filterLecturer, filterLesson, filterTopic);
        }
        return list.stream().map(this::toMaterialResponse).toList();
    }

    @Transactional
    public void deleteMaterial(UserPrincipal actor, String id) {
        Material material = materialRepository.findById(id).orElseThrow(
                () -> new BusinessException(ErrorCode.MATERIAL_NOT_FOUND));

        if (actor.getRole() != RoleName.ADMIN && !material.getLecturerId().equals(actor.getId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền xoá tài liệu của người khác");
        }

        deleteFromGcs(material.getGcsUri());
        deleteRagFileByNameOrFileName(material.getFileName());
        materialRepository.delete(material);
    }

    private void deleteRagFileByNameOrFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) return;
        try {
            String token = getAccessToken();
            String listUrl = String.format(
                    "https://%s-aiplatform.googleapis.com/v1beta1/projects/%s/locations/%s/ragCorpora/%s/ragFiles",
                    location, projectId, location, corpusId);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(listUrl))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode ragFiles = root.path("ragFiles");
                if (ragFiles.isArray()) {
                    for (JsonNode rf : ragFiles) {
                        String name = rf.path("name").asText("");
                        String dName = rf.path("displayName").asText("");
                        if (fileName.equalsIgnoreCase(dName)) {
                            String delUrl = "https://" + location + "-aiplatform.googleapis.com/v1beta1/" + name;
                            HttpRequest delReq = HttpRequest.newBuilder()
                                    .uri(URI.create(delUrl))
                                    .header("Authorization", "Bearer " + token)
                                    .DELETE()
                                    .build();
                            httpClient.send(delReq, HttpResponse.BodyHandlers.ofString());
                            log.info("Deleted RAG file {} from Vertex AI Corpus", name);
                        }
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Không xóa được RAG File trên Vertex AI: {}", fileName, ex);
        }
    }

    @Transactional
    public MaterialResponse reindexMaterial(UserPrincipal actor, String id) {
        Material material = materialRepository.findById(id).orElseThrow(
                () -> new BusinessException(ErrorCode.MATERIAL_NOT_FOUND));

        if (actor.getRole() != RoleName.ADMIN && !material.getLecturerId().equals(actor.getId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền reindex tài liệu của người khác");
        }

        material.setRagStatus("INDEXING");
        material.setErrorMessage(null);
        Material saved = materialRepository.save(material);

        triggerRagImportAsync(saved.getId(), saved.getGcsUri());

        return toMaterialResponse(saved);
    }

    // ==================== GENERATE QUESTIONS FROM RAG ====================

    @Transactional
    public List<QuestionResponse> generateQuestionsFromRag(UserPrincipal actor, GenerateQuestionsFromRagRequest request) {
        Topic topic = topicRepository.findById(request.getTopicId().trim()).orElseThrow(
                () -> new BusinessException(ErrorCode.TOPIC_NOT_FOUND));

        if (actor.getRole() != RoleName.ADMIN && !topic.getLesson().getLecturerId().equals(actor.getId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Chủ đề không thuộc quyền sở hữu của bạn");
        }

        List<Material> materials = materialRepository.findAllById(request.getMaterialIds());
        if (materials.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Không tìm thấy học liệu nào được chọn");
        }

        // Kiểm tra tất cả tài liệu đã READY chưa
        for (Material m : materials) {
            if (!"READY".equalsIgnoreCase(m.getRagStatus())) {
                throw new BusinessException(ErrorCode.MATERIAL_NOT_READY,
                        "Học liệu '" + m.getTitle() + "' đang ở trạng thái " + m.getRagStatus()
                                + ". Chỉ học liệu READY mới có thể sinh câu hỏi Grounded AI.");
            }
        }

        // Gọi AI sinh câu hỏi & rubric
        JsonNode aiGenerated = invokeRagQuestionGeneration(topic, materials, request);

        // Lưu Rubric và Criteria với tổng trọng số đúng 100%
        Rubric rubric = createRubricFromAi(actor, topic, aiGenerated.path("rubric"));

        // Lưu Questions
        List<Question> savedQuestions = new ArrayList<>();
        JsonNode questionsNode = aiGenerated.path("questions");
        if (questionsNode.isArray()) {
            for (JsonNode qNode : questionsNode) {
                String content = qNode.path("content").asText("").trim();
                if (content.isBlank()) {
                    continue;
                }
                String bloomLevel = normalizeBloomLevel(qNode.path("bloomLevel").asText("UNDERSTAND"));
                String modelAnswer = qNode.path("modelAnswer").asText("").trim();

                List<String> keywords = new ArrayList<>();
                JsonNode kwNode = qNode.path("expectedKeywords");
                if (kwNode.isArray()) {
                    for (JsonNode kw : kwNode) {
                        String k = kw.asText("").trim();
                        if (!k.isBlank()) {
                            keywords.add(k);
                        }
                    }
                }

                Question question = Question.builder()
                        .topic(topic)
                        .rubric(rubric)
                        .content(content)
                        .bloomLevel(bloomLevel)
                        .modelAnswer(modelAnswer)
                        .expectedKeywords(contentMapper.toKeywordsJson(keywords))
                        .source("AI_GENERATED")
                        .status("DRAFT")
                        .build();
                question.setId(idGenerator.next("QS"));
                savedQuestions.add(questionRepository.save(question));
            }
        }

        log.info("Đã sinh {} câu hỏi RAG cho topic {}", savedQuestions.size(), topic.getName());
        return savedQuestions.stream()
                .map(q -> contentMapper.toQuestionResponse(q, false))
                .toList();
    }

    private JsonNode invokeRagQuestionGeneration(
            Topic topic,
            List<Material> materials,
            GenerateQuestionsFromRagRequest request) {

        int totalCount = request.getCount() != null ? request.getCount() : 5;
        int remCount = request.getRememberCount() != null ? request.getRememberCount() : 1;
        int undCount = request.getUnderstandCount() != null ? request.getUnderstandCount() : 2;
        int appCount = request.getApplyCount() != null ? request.getApplyCount() : 1;
        int anaCount = request.getAnalyzeCount() != null ? request.getAnalyzeCount() : 1;

        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("Bạn là chuyên gia học thuật và giám khảo thi vấn đáp đại học.\n");
        promptBuilder.append("Hãy nghiên cứu kỹ tài liệu bài giảng/slide trong RAG Corpus để tạo bộ câu hỏi thi vấn đáp ");
        promptBuilder.append("và 01 khung tiêu chí chấm điểm chuẩn (Rubric) cho môn học '")
                .append(topic.getLesson().getTitle())
                .append("', chủ đề: '")
                .append(topic.getName())
                .append("'.\n\n");

        if (topic.getDescription() != null && !topic.getDescription().isBlank()) {
            promptBuilder.append("Mục tiêu chủ đề: ").append(topic.getDescription()).append("\n\n");
        }

        promptBuilder.append("Danh sách slide/tài liệu tham chiếu:\n");
        for (Material m : materials) {
            promptBuilder.append("- ").append(m.getTitle()).append(" (File: ").append(m.getFileName()).append(")\n");
        }
        promptBuilder.append("\n");

        promptBuilder.append("Cấu trúc số lượng câu hỏi cần sinh:\n");
        promptBuilder.append("- Tổng số câu hỏi: ").append(totalCount).append("\n");
        promptBuilder.append("- Mức REMEMBER (Nhớ, nêu định nghĩa): ").append(remCount).append("\n");
        promptBuilder.append("- Mức UNDERSTAND (Hiểu, giải thích nguyên lý): ").append(undCount).append("\n");
        promptBuilder.append("- Mức APPLY (Vận dụng vào bài toán thực tiễn): ").append(appCount).append("\n");
        promptBuilder.append("- Mức ANALYZE (Phân tích, so sánh, phản biện): ").append(anaCount).append("\n\n");

        promptBuilder.append("Quy tắc bắt buộc:\n");
        promptBuilder.append("1. Câu hỏi và đáp án phải bám sát (grounded) 100% vào ngữ liệu của slide tài liệu.\n");
        promptBuilder.append("2. Mỗi câu hỏi phải có đáp án mẫu chuẩn mực học thuật, đủ ý để chấm thi vấn đáp.\n");
        promptBuilder.append("3. expectedKeywords: 3 - 6 từ khoá then chốt bắt buộc có trong câu trả lời của thí sinh.\n");
        promptBuilder.append("4. Tạo 01 Rubric đánh giá chung với 3 đến 4 tiêu chí. Tổng trọng số (weightPercent) của các tiêu chí PHẢI BẰNG ĐÚNG 100%.\n");
        promptBuilder.append("5. Chỉ trả về DUY NHẤT một chuỗi JSON hợp lệ, không chứa văn bản ngoài JSON, theo schema sau:\n");
        promptBuilder.append("""
        {
          "rubric": {
            "name": "Tiêu chí chấm thi vấn đáp - %s",
            "description": "Thang đo đánh giá chất lượng câu trả lời thi vấn đáp",
            "criteria": [
              {
                "name": "Nắm vững lý thuyết trọng tâm",
                "description": "Trình bày chính xác định nghĩa, cơ chế và thuật ngữ theo tài liệu",
                "weightPercent": 40.0
              },
              {
                "name": "Khả năng phân tích và giải thích logic",
                "description": "Lập luận chặt chẽ, diễn đạt rõ ràng mạch lạc",
                "weightPercent": 30.0
              },
              {
                "name": "Vận dụng thực tế và ví dụ minh hoạ",
                "description": "Liên hệ tốt với tình huống thực tiễn và giải quyết vấn đề",
                "weightPercent": 30.0
              }
            ]
          },
          "questions": [
            {
              "content": "Nội dung câu hỏi...",
              "bloomLevel": "UNDERSTAND",
              "modelAnswer": "Nội dung đáp án chuẩn...",
              "expectedKeywords": ["từ khoá 1", "từ khoá 2", "từ khoá 3"]
            }
          ]
        }
        """.formatted(topic.getName()));

        String prompt = promptBuilder.toString();

        // 1. Thử gọi Vertex AI Grounded RAG
        try {
            String vertexResponse = callVertexAiRag(prompt);
            if (vertexResponse != null && !vertexResponse.isBlank()) {
                return objectMapper.readTree(vertexResponse);
            }
        } catch (Exception ex) {
            log.warn("Gọi Vertex AI RAG Grounded inference gặp lỗi, chuyển sang fallback LLM: {}", ex.getMessage());
        }

        // 2. Fallback: Dùng LlmClient tích hợp sẵn
        try {
            Map<String, Object> schema = buildGenerationResponseSchema();
            String llmResponse = llmClient.generateJson("Bạn là trợ lý khảo thí đại học chuyên tạo đề thi vấn đáp.", prompt, schema);
            return objectMapper.readTree(llmResponse);
        } catch (Exception ex) {
            log.error("Fallback LlmClient cũng thất bại", ex);
            throw new BusinessException(ErrorCode.AI_REQUEST_FAILED, "Không thể sinh câu hỏi từ tài liệu RAG: " + ex.getMessage());
        }
    }

    private String callVertexAiRag(String prompt) throws Exception {
        String token = getAccessToken();
        String url = String.format(
                "https://%s-aiplatform.googleapis.com/v1beta1/projects/%s/locations/%s/publishers/google/models/gemini-1.5-flash:generateContent",
                location, projectId, location);

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of(
                                "role", "user",
                                "parts", List.of(
                                        Map.of("text", prompt)
                                )
                        )
                ),
                "tools", List.of(
                        Map.of(
                                "retrieval", Map.of(
                                        "vertexRagStore", Map.of(
                                                "ragResources", List.of(
                                                        Map.of("ragCorpus", String.format(
                                                                "projects/%s/locations/%s/ragCorpora/%s",
                                                                projectId, location, corpusId))
                                                ),
                                                "similarityTopK", 5
                                        )
                                )
                        )
                ),
                "generationConfig", Map.of(
                        "temperature", 0.3,
                        "responseMimeType", "application/json"
                )
        );

        String payload = objectMapper.writeValueAsString(requestBody);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && !candidates.isEmpty()) {
                JsonNode parts = candidates.get(0).path("content").path("parts");
                if (parts.isArray() && !parts.isEmpty()) {
                    return parts.get(0).path("text").asText();
                }
            }
        }
        log.warn("Vertex AI response code: {}, body: {}", response.statusCode(), response.body());
        return null;
    }

    private Rubric createRubricFromAi(UserPrincipal actor, Topic topic, JsonNode rubricNode) {
        String name = rubricNode.path("name").asText("Tiêu chí chấm thi vấn đáp - " + topic.getName());
        String description = rubricNode.path("description").asText("Khung tiêu chí chấm thi được AI tự động sinh theo slide học liệu");

        Rubric rubric = Rubric.builder()
                .createdBy(actor.getId())
                .name(name.trim())
                .description(description)
                .maxScore(new BigDecimal("10.00"))
                .build();
        rubric.setId(idGenerator.next("RB"));
        Rubric savedRubric = rubricRepository.save(rubric);

        List<RubricCriterion> criteria = new ArrayList<>();
        JsonNode criteriaNode = rubricNode.path("criteria");
        if (criteriaNode.isArray() && !criteriaNode.isEmpty()) {
            int orderNo = 1;
            for (JsonNode cNode : criteriaNode) {
                String cName = cNode.path("name").asText("Tiêu chí " + orderNo);
                String cDesc = cNode.path("description").asText("Yêu cầu đạt điểm cho tiêu chí " + orderNo);
                double weightVal = cNode.path("weightPercent").asDouble(25.0);
                BigDecimal weight = BigDecimal.valueOf(weightVal).setScale(2, RoundingMode.HALF_UP);

                RubricCriterion criterion = RubricCriterion.builder()
                        .rubric(savedRubric)
                        .name(cName.trim())
                        .description(cDesc.trim())
                        .weightPercent(weight)
                        .orderNo(orderNo++)
                        .build();
                criterion.setId(idGenerator.next("RC"));
                criteria.add(criterion);
            }
        }

        // Đảm bảo có ít nhất 1 tiêu chí và tổng trọng số bằng đúng 100.00%
        if (criteria.isEmpty()) {
            RubricCriterion defaultCriterion = RubricCriterion.builder()
                    .rubric(savedRubric)
                    .name("Độ chính xác nội dung")
                    .description("Nắm vững và trả lời đúng trọng tâm câu hỏi")
                    .weightPercent(FULL_WEIGHT)
                    .orderNo(1)
                    .build();
            defaultCriterion.setId(idGenerator.next("RC"));
            criteria.add(defaultCriterion);
        } else {
            BigDecimal total = criteria.stream()
                    .map(RubricCriterion::getWeightPercent)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (total.compareTo(FULL_WEIGHT) != 0) {
                BigDecimal diff = FULL_WEIGHT.subtract(total);
                RubricCriterion last = criteria.get(criteria.size() - 1);
                last.setWeightPercent(last.getWeightPercent().add(diff));
            }
        }

        criterionRepository.saveAll(criteria);
        return savedRubric;
    }

    private Map<String, Object> buildGenerationResponseSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "rubric", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "name", Map.of("type", "string"),
                                        "description", Map.of("type", "string"),
                                        "criteria", Map.of(
                                                "type", "array",
                                                "items", Map.of(
                                                        "type", "object",
                                                        "properties", Map.of(
                                                                "name", Map.of("type", "string"),
                                                                "description", Map.of("type", "string"),
                                                                "weightPercent", Map.of("type", "number")
                                                        ),
                                                        "required", List.of("name", "description", "weightPercent")
                                                )
                                        )
                                ),
                                "required", List.of("name", "criteria")
                        ),
                        "questions", Map.of(
                                "type", "array",
                                "items", Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "content", Map.of("type", "string"),
                                                "bloomLevel", Map.of("type", "string"),
                                                "modelAnswer", Map.of("type", "string"),
                                                "expectedKeywords", Map.of("type", "array", "items", Map.of("type", "string"))
                                        ),
                                        "required", List.of("content", "bloomLevel", "modelAnswer")
                                )
                        )
                ),
                "required", List.of("rubric", "questions")
        );
    }

    private MaterialResponse toMaterialResponse(Material m) {
        String lessonTitle = null;
        if (m.getLessonId() != null) {
            lessonTitle = lessonRepository.findById(m.getLessonId())
                    .map(Lesson::getTitle)
                    .orElse(null);
        }
        String topicName = null;
        if (m.getTopicId() != null) {
            topicName = topicRepository.findById(m.getTopicId())
                    .map(Topic::getName)
                    .orElse(null);
        }

        return MaterialResponse.builder()
                .id(m.getId())
                .lecturerId(m.getLecturerId())
                .lessonId(m.getLessonId())
                .lessonTitle(lessonTitle)
                .topicId(m.getTopicId())
                .topicName(topicName)
                .title(m.getTitle())
                .fileName(m.getFileName())
                .fileSize(m.getFileSize())
                .fileType(m.getFileType())
                .gcsUri(m.getGcsUri())
                .ragCorpusId(m.getRagCorpusId())
                .ragStatus(m.getRagStatus())
                .chunkCount(m.getChunkCount())
                .errorMessage(m.getErrorMessage())
                .createdAt(AuditTime.toInstant(m.getCreatedAt()))
                .build();
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1);
    }

    private String normalizeBloomLevel(String bloomLevel) {
        if (bloomLevel == null || bloomLevel.isBlank()) {
            return "UNDERSTAND";
        }
        String norm = bloomLevel.trim().toUpperCase(Locale.ROOT);
        return VALID_BLOOM_LEVELS.contains(norm) ? norm : "UNDERSTAND";
    }
}
