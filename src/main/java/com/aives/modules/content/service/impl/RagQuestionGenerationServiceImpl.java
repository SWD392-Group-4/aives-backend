package com.aives.modules.content.service.impl;

import com.aives.common.exception.BusinessException;
import com.aives.common.exception.ErrorCode;
import com.aives.common.security.UserPrincipal;
import com.aives.common.util.AuditTime;
import com.aives.common.util.RandomIdGenerator;
import com.aives.integration.ai.LlmClient;
import com.aives.integration.ai.LlmRequest;
import com.aives.modules.auth.entity.User;
import com.aives.modules.auth.repository.UserRepository;
import com.aives.modules.content.dto.DifficultyRatioConfig;
import com.aives.modules.content.dto.GenerateQuestionsRequest;
import com.aives.modules.content.dto.QuestionResponse;
import com.aives.modules.content.entity.Chapter;
import com.aives.modules.content.entity.Course;
import com.aives.modules.content.entity.Material;
import com.aives.modules.content.entity.Question;
import com.aives.modules.content.entity.Rubric;
import com.aives.modules.content.enums.AuditAction;
import com.aives.modules.content.enums.QuestionDifficulty;
import com.aives.modules.content.enums.QuestionSource;
import com.aives.modules.content.enums.QuestionStatus;
import com.aives.modules.content.mapper.ContentMapper;
import com.aives.modules.content.repository.ChapterRepository;
import com.aives.modules.content.repository.MaterialRepository;
import com.aives.modules.content.repository.QuestionRepository;
import com.aives.modules.content.repository.RubricRepository;
import com.aives.modules.content.service.AcademicAuditLogService;
import com.aives.modules.content.service.RagQuestionGenerationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagQuestionGenerationServiceImpl implements RagQuestionGenerationService {

    private final ChapterRepository chapterRepository;
    private final MaterialRepository materialRepository;
    private final RubricRepository rubricRepository;
    private final QuestionRepository questionRepository;
    private final UserRepository userRepository;
    private final AcademicAuditLogService academicAuditLogService;
    private final ContentMapper contentMapper;
    private final RandomIdGenerator idGenerator;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public List<QuestionResponse> generateQuestions(
            String chapterId, GenerateQuestionsRequest request, UserPrincipal principal) {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAPTER_NOT_FOUND, "Không tìm thấy chương: " + chapterId));

        Course course = chapter.getCourse();
        DifficultyRatioConfig ratio = request.getDifficultyRatio();
        validateDifficultyRatio(ratio);

        List<Material> materials = materialRepository.findAllById(request.getMaterialIds());
        if (materials.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Không tìm thấy học liệu nào được chọn");
        }

        for (Material m : materials) {
            if (m.getChapterId() != null && !chapterId.equals(m.getChapterId())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Học liệu '" + m.getTitle() + "' không thuộc chương đang chọn");
            }
            if (!"READY".equalsIgnoreCase(m.getRagStatus())) {
                throw new BusinessException(ErrorCode.MATERIAL_NOT_READY,
                        "Học liệu '" + m.getTitle() + "' đang ở trạng thái " + m.getRagStatus()
                                + ". Chỉ học liệu READY mới có thể dùng làm ngữ cảnh sinh câu hỏi RAG.");
            }
        }

        Rubric courseRubric = rubricRepository.findByCourse_Id(course.getId()).orElse(null);

        int total = request.getTotalQuestions() != null ? request.getTotalQuestions() : 5;
        int easyCount = Math.round(total * ratio.getEasyPercent() / 100.0f);
        int mediumCount = Math.round(total * ratio.getMediumPercent() / 100.0f);
        int hardCount = total - easyCount - mediumCount;
        if (hardCount < 0) {
            hardCount = 0;
            mediumCount = total - easyCount;
        }

        List<GeneratedQuestionData> generatedDataList = invokeLlmQuestionGeneration(
                course, chapter, materials, total, easyCount, mediumCount, hardCount);

        List<Question> questionsToSave = new ArrayList<>();
        for (GeneratedQuestionData data : generatedDataList) {
            Question q = Question.builder()
                    .chapter(chapter)
                    .rubric(courseRubric)
                    .content(data.content())
                    .difficulty(data.difficulty())
                    .modelAnswer(data.modelAnswer())
                    .expectedKeywords(contentMapper.toKeywordsJson(data.expectedKeywords()))
                    .source(QuestionSource.AI_GENERATED.name())
                    .status(QuestionStatus.DRAFT.name())
                    .createdBy(principal.getId())
                    .build();
            q.setId(idGenerator.next("QS"));
            questionsToSave.add(q);
        }

        List<Question> savedQuestions = questionRepository.saveAll(questionsToSave);
        log.info("Saved {} AI-generated questions for chapter: id={}, courseId={}",
                savedQuestions.size(), chapter.getId(), course.getId());

        String actorName = principal.getEmail();
        if (principal.getId() != null) {
            actorName = userRepository.findById(principal.getId())
                    .map(User::getFullName)
                    .orElse(principal.getEmail());
        }

        academicAuditLogService.logActivity(
                course.getId(),
                chapter.getId(),
                principal.getId(),
                actorName,
                AuditAction.AI_GENERATE_QUESTION,
                "QUESTION",
                chapter.getId(),
                Map.of(
                        "totalQuestions", savedQuestions.size(),
                        "easyCount", easyCount,
                        "mediumCount", mediumCount,
                        "hardCount", hardCount,
                        "materialCount", materials.size()
                )
        );

        return savedQuestions.stream()
                .map(q -> contentMapper.toQuestionResponse(q, false))
                .toList();
    }

    private void validateDifficultyRatio(DifficultyRatioConfig ratio) {
        if (ratio == null) {
            throw new BusinessException(ErrorCode.DIFFICULTY_RATIO_INVALID, "Cấu hình tỷ lệ độ khó không được để trống");
        }
        int easy = ratio.getEasyPercent() != null ? ratio.getEasyPercent() : 0;
        int medium = ratio.getMediumPercent() != null ? ratio.getMediumPercent() : 0;
        int hard = ratio.getHardPercent() != null ? ratio.getHardPercent() : 0;

        if (easy + medium + hard != 100) {
            throw new BusinessException(ErrorCode.DIFFICULTY_RATIO_INVALID,
                    "Tổng tỷ lệ phần trăm độ khó (Dễ + Trung bình + Khó) phải bằng đúng 100%. Hiện tại: "
                            + (easy + medium + hard) + "%");
        }
    }

    private List<GeneratedQuestionData> invokeLlmQuestionGeneration(
            Course course, Chapter chapter, List<Material> materials,
            int total, int easyCount, int mediumCount, int hardCount) {

        if (!llmClient.isEnabled()) {
            return generateFallbackQuestions(course, chapter, total, easyCount, mediumCount, hardCount);
        }

        try {
            StringBuilder system = new StringBuilder();
            system.append("Bạn là chuyên gia khảo thí và giám khảo thi vấn đáp đại học môn '")
                    .append(course.getName())
                    .append("'. Hãy tạo danh sách câu hỏi thi vấn đáp grounded 100% trên nội dung học liệu.\n")
                    .append("Quy tắc bắt buộc (BR-BANK-002, BR-BANK-004):\n")
                    .append("1. TUYỆT ĐỐI KHÔNG TẠO RUBRIC. Chỉ sinh nội dung câu hỏi, độ khó, đáp án mẫu, từ khoá.\n")
                    .append("2. Phân loại độ khó bắt buộc dùng đúng 3 giá trị: 'EASY', 'MEDIUM', 'HARD'.\n")
                    .append("3. expectedKeywords: từ 3 đến 6 từ khóa chuyên ngành bắt buộc.");

            StringBuilder user = new StringBuilder();
            user.append("Môn học: ").append(course.getName()).append(" (").append(course.getCourseCode()).append(")\n");
            user.append("Chương: ").append(chapter.getName()).append("\n");
            if (chapter.getDescription() != null) {
                user.append("Mô tả chương: ").append(chapter.getDescription()).append("\n");
            }
            user.append("Danh sách slide/tài liệu tham chiếu:\n");
            for (Material m : materials) {
                user.append("- ").append(m.getTitle()).append(" (File: ").append(m.getFileName()).append(")\n");
            }
            user.append("\nYêu cầu phân bổ số lượng câu hỏi:\n");
            user.append("- Tổng số câu hỏi: ").append(total).append("\n");
            user.append("- Số câu EASY (Dễ - nhận biết & định nghĩa): ").append(easyCount).append("\n");
            user.append("- Số câu MEDIUM (Trung bình - thông hiểu & vận dụng): ").append(mediumCount).append("\n");
            user.append("- Số câu HARD (Khó - phân tích sâu, so sánh & tình huống): ").append(hardCount).append("\n");
            user.append("\nTrả về JSON duy nhất theo schema: { \"questions\": [ { \"content\": \"...\", \"difficulty\": \"EASY|MEDIUM|HARD\", \"modelAnswer\": \"...\", \"expectedKeywords\": [\"...\"] } ] }");

            Map<String, Object> schema = Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "questions", Map.of(
                                    "type", "array",
                                    "items", Map.of(
                                            "type", "object",
                                            "properties", Map.of(
                                                    "content", Map.of("type", "string"),
                                                    "difficulty", Map.of("type", "string", "enum", List.of("EASY", "MEDIUM", "HARD")),
                                                    "modelAnswer", Map.of("type", "string"),
                                                    "expectedKeywords", Map.of("type", "array", "items", Map.of("type", "string"))
                                            ),
                                            "required", List.of("content", "difficulty", "modelAnswer", "expectedKeywords")
                                    )
                            )
                    ),
                    "required", List.of("questions")
            );

            LlmRequest llmRequest = LlmRequest.longRunning(system.toString(), user.toString(), schema);
            String rawJson = llmClient.generateJson(llmRequest);
            JsonNode root = objectMapper.readTree(rawJson);
            JsonNode arr = root.path("questions");

            List<GeneratedQuestionData> results = new ArrayList<>();
            if (arr.isArray()) {
                for (JsonNode item : arr) {
                    String content = item.path("content").asText();
                    String diff = QuestionDifficulty.fromString(item.path("difficulty").asText()).name();
                    String modelAnswer = item.path("modelAnswer").asText();
                    List<String> keywords = new ArrayList<>();
                    JsonNode kwNode = item.path("expectedKeywords");
                    if (kwNode.isArray()) {
                        for (JsonNode k : kwNode) {
                            if (!k.asText().isBlank()) {
                                keywords.add(k.asText().trim());
                            }
                        }
                    }
                    results.add(new GeneratedQuestionData(content, diff, modelAnswer, keywords));
                }
            }

            if (!results.isEmpty()) {
                return results;
            }
        } catch (Exception ex) {
            log.warn("LLM question generation encountered error, falling back: {}", ex.getMessage());
        }

        return generateFallbackQuestions(course, chapter, total, easyCount, mediumCount, hardCount);
    }

    private List<GeneratedQuestionData> generateFallbackQuestions(
            Course course, Chapter chapter, int total, int easyCount, int mediumCount, int hardCount) {
        List<GeneratedQuestionData> list = new ArrayList<>();
        int idx = 1;

        for (int i = 0; i < easyCount; i++) {
            list.add(new GeneratedQuestionData(
                    "Nêu định nghĩa và khái niệm cơ bản về các nguyên lý trong " + chapter.getName() + " (" + course.getName() + ")?",
                    "EASY",
                    "Khái niệm cốt lõi bao gồm các định nghĩa nền tảng được trình bày trong bài giảng.",
                    List.of("định nghĩa", "nguyên lý", "khái niệm", "cốt lõi")
            ));
            idx++;
        }

        for (int i = 0; i < mediumCount; i++) {
            list.add(new GeneratedQuestionData(
                    "Giải thích cách thức vận hành và trường hợp áp dụng phù hợp của " + chapter.getName() + "?",
                    "MEDIUM",
                    "Nguyên tắc vận hành dựa trên việc áp dụng chuẩn thiết kế nhằm tối ưu cấu trúc phần mềm.",
                    List.of("vận hành", "áp dụng", "thiết kế", "tối ưu")
            ));
            idx++;
        }

        for (int i = 0; i < hardCount; i++) {
            list.add(new GeneratedQuestionData(
                    "Phân tích ưu nhược điểm và so sánh phương án kiến trúc trong " + chapter.getName() + " khi xử lý bài toán chịu tải cao?",
                    "HARD",
                    "Cần đánh giá sự đánh đổi giữa hiệu năng, độ phức tạp và khả năng mở rộng của hệ thống.",
                    List.of("phân tích", "đánh đổi", "hiệu năng", "khả năng mở rộng")
            ));
            idx++;
        }

        return list;
    }

    private record GeneratedQuestionData(
            String content,
            String difficulty,
            String modelAnswer,
            List<String> expectedKeywords
    ) {}
}
