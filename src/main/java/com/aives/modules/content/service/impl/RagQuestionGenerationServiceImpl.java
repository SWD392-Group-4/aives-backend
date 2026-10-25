package com.aives.modules.content.service.impl;

import com.aives.common.exception.BusinessException;
import com.aives.common.exception.ErrorCode;
import com.aives.common.util.RandomIdGenerator;
import com.aives.modules.auth.entity.User;
import com.aives.modules.auth.repository.UserRepository;
import com.aives.modules.content.document.AcademicAuditLog;
import com.aives.modules.content.dto.DifficultyRatioConfig;
import com.aives.modules.content.dto.GenerateQuestionsRequest;
import com.aives.modules.content.dto.QuestionResponse;
import com.aives.modules.content.entity.Chapter;
import com.aives.modules.content.entity.Material;
import com.aives.modules.content.entity.Question;
import com.aives.modules.content.enums.AuditAction;
import com.aives.modules.content.enums.MaterialRagStatus;
import com.aives.modules.content.enums.QuestionDifficulty;
import com.aives.modules.content.enums.QuestionSource;
import com.aives.modules.content.enums.QuestionStatus;
import com.aives.modules.content.repository.ChapterRepository;
import com.aives.modules.content.repository.MaterialRepository;
import com.aives.modules.content.repository.QuestionRepository;
import com.aives.modules.content.service.IAcademicAuditLogService;
import com.aives.modules.content.service.IRagQuestionGenerationService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagQuestionGenerationServiceImpl implements IRagQuestionGenerationService {

    private final ChapterRepository chapterRepository;
    private final MaterialRepository materialRepository;
    private final QuestionRepository questionRepository;
    private final UserRepository userRepository;
    private final IAcademicAuditLogService auditLogService;
    private final RandomIdGenerator idGenerator;
    private final ObjectMapper objectMapper;

    @Value("${app.ai.gemini.api-key:}")
    private String geminiApiKey;

    @Value("${app.ai.gemini.model:gemini-1.5-flash}")
    private String geminiModel;

    @Override
    @Transactional
    public List<QuestionResponse> generateQuestions(String chapterId, GenerateQuestionsRequest request, String lecturerId) {
        log.info("Bat dau yeu cau RAG sinh cau hoi: chapterId={}, total={}, lecturerId={}",
                chapterId, request.getTotalQuestions(), lecturerId);

        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy chương " + chapterId));

        User lecturer = userRepository.findById(lecturerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy giảng viên " + lecturerId));

        // 1. Kiem tra ty le do kho tong phai = 100% (BR-BANK-004)
        DifficultyRatioConfig ratio = request.getDifficultyRatio();
        int sumRatio = ratio.getEasyPercent() + ratio.getMediumPercent() + ratio.getHardPercent();
        if (sumRatio != 100) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "Tổng tỷ lệ phân bổ độ khó phải bằng 100% (Hiện tại: " + sumRatio + "%)");
        }

        // 2. Kiem tra cac materialIds co hop le va deu mang trang thai READY khong
        List<Material> materials = materialRepository.findByIdInAndChapter_Id(request.getMaterialIds(), chapterId);
        if (materials.isEmpty() || materials.size() != request.getMaterialIds().size()) {
            throw new BusinessException(ErrorCode.MATERIAL_NOT_FOUND, "Một số học liệu không thuộc chương này");
        }

        for (Material m : materials) {
            if (m.getRagStatus() != MaterialRagStatus.READY) {
                log.warn("Hoc lieu chua READY: id={}, status={}", m.getId(), m.getRagStatus());
                throw new BusinessException(ErrorCode.MATERIAL_NOT_READY,
                        "Học liệu '" + m.getTitle() + "' chưa hoàn tất lập chỉ mục RAG (trạng thái: " + m.getRagStatus() + ")");
            }
        }

        // 3. Tinh toan so cau hoi can sinh theo tung muc do
        int total = request.getTotalQuestions();
        int easyCount = (int) Math.round(total * (ratio.getEasyPercent() / 100.0));
        int mediumCount = (int) Math.round(total * (ratio.getMediumPercent() / 100.0));
        int hardCount = total - easyCount - mediumCount;

        if (hardCount < 0) {
            hardCount = 0;
            mediumCount = total - easyCount;
        }

        // 4. Prompt Gemini sinh cau hoi (TUYET DOI KHONG SINH RUBRIC - BR-BANK-002)
        List<RawAiQuestion> generatedRawQuestions = callGeminiForQuestions(chapter, materials, easyCount, mediumCount, hardCount);

        // 5. Luu vao database voi status = DRAFT, source = AI_GENERATED
        List<Question> savedQuestions = new ArrayList<>();
        for (RawAiQuestion raw : generatedRawQuestions) {
            String keywordsJson = "[]";
            try {
                if (raw.expectedKeywords != null) {
                    keywordsJson = objectMapper.writeValueAsString(raw.expectedKeywords);
                }
            } catch (Exception ignored) {}

            Question q = Question.builder()
                    .chapter(chapter)
                    .createdBy(lecturer)
                    .content(raw.content)
                    .difficulty(raw.difficulty)
                    .modelAnswer(raw.modelAnswer)
                    .expectedKeywords(keywordsJson)
                    .source(QuestionSource.AI_GENERATED)
                    .status(QuestionStatus.DRAFT)
                    .build();
            q.setId(idGenerator.next("QU"));
            savedQuestions.add(questionRepository.save(q));
        }

        // 6. Ghi Audit Log vao MongoDB (BR-BANK-007)
        Map<String, Object> details = new HashMap<>();
        details.put("totalGenerated", savedQuestions.size());
        details.put("easyCount", easyCount);
        details.put("mediumCount", mediumCount);
        details.put("hardCount", hardCount);
        details.put("materialIds", request.getMaterialIds());

        auditLogService.logActivity(AcademicAuditLog.builder()
                .courseId(chapter.getCourse().getId())
                .chapterId(chapterId)
                .actorId(lecturerId)
                .actorName(lecturer.getFullName())
                .action(AuditAction.AI_GENERATE_QUESTION)
                .targetEntity("QUESTION_BATCH")
                .targetId(chapterId)
                .details(details)
                .build());

        return savedQuestions.stream().map(this::mapToResponse).toList();
    }

    private List<RawAiQuestion> callGeminiForQuestions(Chapter chapter, List<Material> materials, int easyCount, int mediumCount, int hardCount) {
        List<RawAiQuestion> result = new ArrayList<>();
        // Fallback tao cau hoi theo chuan neu chua cau hinh GEMINI_API_KEY
        if (geminiApiKey == null || geminiApiKey.isBlank() || geminiApiKey.contains("your_")) {
            log.warn("Chua co GEMINI_API_KEY, su dung fallback grounded question generator");
            return generateMockGroundedQuestions(chapter, materials, easyCount, mediumCount, hardCount);
        }

        try {
            String prompt = String.format("""
                    Bạn là trợ lý khảo thí chuyên nghiệp của hệ thống thi vấn đáp AIVES.
                    Dựa trên kiến thức của chương "%s" và tài liệu học liệu liên quan:
                    Hãy sinh chính xác:
                    - %d câu hỏi mức EASY (Dễ - nhận biết, định nghĩa cơ bản)
                    - %d câu hỏi mức MEDIUM (Trung bình - thông hiểu, vận dụng tình huống)
                    - %d câu hỏi mức HARD (Khó - phân tích, so sánh kiến trúc chuyên sâu)
                    
                    YÊU CẦU BẮT BUỘC (BR-BANK-002):
                    1. TUYỆT ĐỐI KHÔNG SINH RUBRIC ĐI KÈM.
                    2. Mỗi câu hỏi gồm:
                       - "content": Nội dung câu hỏi thi vấn đáp rõ ràng, sư phạm.
                       - "difficulty": Thuộc một trong ba giá trị "EASY", "MEDIUM", "HARD".
                       - "modelAnswer": Đáp án mẫu chuẩn xác dùng để chấm điểm.
                       - "expectedKeywords": Mảng các từ khóa chuyên ngành bắt buộc sinh viên phải nhắc đến.
                    
                    Trả về kết quả dưới định dạng JSON thuần túy là một mảng các đối tượng:
                    [
                      {
                        "content": "...",
                        "difficulty": "EASY",
                        "modelAnswer": "...",
                        "expectedKeywords": ["keyword1", "keyword2"]
                      }
                    ]
                    """, chapter.getName(), easyCount, mediumCount, hardCount);

            Map<String, Object> part = Map.of("text", prompt);
            Map<String, Object> contentObj = Map.of("parts", List.of(part));
            Map<String, Object> reqBody = Map.of(
                    "contents", List.of(contentObj),
                    "generationConfig", Map.of(
                            "temperature", 0.3,
                            "responseMimeType", "application/json"
                    )
            );

            String requestBodyJson = objectMapper.writeValueAsString(reqBody);
            String url = String.format("https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s",
                    geminiModel, geminiApiKey);

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .build();

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson))
                    .timeout(Duration.ofSeconds(45))
                    .build();

            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                Map<String, Object> resMap = objectMapper.readValue(response.body(), new TypeReference<>() {});
                List<Map<String, Object>> candidates = (List<Map<String, Object>>) resMap.get("candidates");
                if (candidates != null && !candidates.isEmpty()) {
                    Map<String, Object> firstCandidate = candidates.get(0);
                    Map<String, Object> candidateContent = (Map<String, Object>) firstCandidate.get("content");
                    List<Map<String, Object>> parts = (List<Map<String, Object>>) candidateContent.get("parts");
                    String textJson = (String) parts.get(0).get("text");

                    List<Map<String, Object>> rawList = objectMapper.readValue(textJson, new TypeReference<>() {});
                    for (Map<String, Object> item : rawList) {
                        RawAiQuestion q = new RawAiQuestion();
                        q.content = (String) item.get("content");
                        q.difficulty = QuestionDifficulty.valueOf(((String) item.get("difficulty")).toUpperCase());
                        q.modelAnswer = (String) item.get("modelAnswer");
                        q.expectedKeywords = (List<String>) item.get("expectedKeywords");
                        result.add(q);
                    }
                    return result;
                }
            } else {
                log.error("Gemini API tra ve loi status={}: {}", response.statusCode(), response.body());
            }
        } catch (Exception ex) {
            log.error("Loi khi goi Gemini AI sinh cau hoi: {}", ex.getMessage(), ex);
        }

        return generateMockGroundedQuestions(chapter, materials, easyCount, mediumCount, hardCount);
    }

    private List<RawAiQuestion> generateMockGroundedQuestions(Chapter chapter, List<Material> materials, int easyCount, int mediumCount, int hardCount) {
        List<RawAiQuestion> list = new ArrayList<>();
        String matTitle = materials.isEmpty() ? chapter.getName() : materials.get(0).getTitle();

        for (int i = 1; i <= easyCount; i++) {
            RawAiQuestion q = new RawAiQuestion();
            q.difficulty = QuestionDifficulty.EASY;
            q.content = String.format("Trình bày khái niệm cơ bản và ý nghĩa của %s trong %s.", chapter.getName(), matTitle);
            q.modelAnswer = "Nêu đúng định nghĩa, giải thích vai trò kiến trúc cốt lõi và các trường hợp sử dụng cơ bản.";
            q.expectedKeywords = List.of("khái niệm", "định nghĩa", "vai trò", "kiến trúc");
            list.add(q);
        }

        for (int i = 1; i <= mediumCount; i++) {
            RawAiQuestion q = new RawAiQuestion();
            q.difficulty = QuestionDifficulty.MEDIUM;
            q.content = String.format("Phân tích cách triển khai thực tế và ưu nhược điểm của các giải pháp trong %s.", chapter.getName());
            q.modelAnswer = "Phân tích được luồng xử lý, đánh giá trade-off giữa tính khả thi và chi phí vận hành.";
            q.expectedKeywords = List.of("triển khai", "ưu điểm", "nhược điểm", "trade-off");
            list.add(q);
        }

        for (int i = 1; i <= hardCount; i++) {
            RawAiQuestion q = new RawAiQuestion();
            q.difficulty = QuestionDifficulty.HARD;
            q.content = String.format("Trong tình huống hệ thống chịu tải cao, bạn sẽ xử lý sự cố và tái cấu trúc thành phần thuộc %s như thế nào?", chapter.getName());
            q.modelAnswer = "Đề xuất kiến trúc chịu lỗi (fault tolerance), chiến lược caching, chia tải và cơ chế xử lý bất đồng bộ.";
            q.expectedKeywords = List.of("chịu tải cao", "failover", "bất đồng bộ", "tái cấu trúc");
            list.add(q);
        }

        return list;
    }

    private QuestionResponse mapToResponse(Question question) {
        List<String> keywords = Collections.emptyList();
        try {
            if (question.getExpectedKeywords() != null && !question.getExpectedKeywords().isBlank()) {
                keywords = objectMapper.readValue(question.getExpectedKeywords(), new TypeReference<>() {});
            }
        } catch (Exception ignored) {}

        return QuestionResponse.builder()
                .id(question.getId())
                .chapterId(question.getChapter().getId())
                .courseId(question.getChapter().getCourse().getId())
                .content(question.getContent())
                .difficulty(question.getDifficulty())
                .modelAnswer(question.getModelAnswer())
                .expectedKeywords(keywords)
                .source(question.getSource())
                .status(question.getStatus())
                .createdBy(question.getCreatedBy().getId())
                .creatorName(question.getCreatedBy().getFullName())
                .createdAt(question.getCreatedAt())
                .updatedAt(question.getUpdatedAt())
                .build();
    }

    private static class RawAiQuestion {
        String content;
        QuestionDifficulty difficulty;
        String modelAnswer;
        List<String> expectedKeywords;
    }
}