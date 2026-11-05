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
import com.aives.modules.content.dto.GenerateCourseQuestionsRequest;
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
import com.aives.modules.content.repository.CourseRepository;
import com.aives.modules.content.repository.MaterialRepository;
import com.aives.modules.content.repository.QuestionRepository;
import com.aives.modules.content.repository.RubricRepository;
import com.aives.modules.content.service.AcademicAuditLogService;
import com.aives.modules.content.service.CourseRubricService;
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

    /** Gioi han tong so ky tu noi dung tai lieu dua vao prompt. */
    private static final int MAX_CONTEXT_CHARS = 60_000;

    private final ChapterRepository chapterRepository;
    private final CourseRepository courseRepository;
    private final CourseRubricService courseRubricService;
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

    @Override
    @Transactional
    public List<QuestionResponse> generateCourseQuestions(
            String courseId, GenerateCourseQuestionsRequest request, UserPrincipal principal) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND, "Không tìm thấy môn học: " + courseId));

        // Rubric phai duyet truoc roi moi tao ngan hang cau hoi
        if (!courseRubricService.isRubricReadyForGrading(course.getId())) {
            throw new BusinessException(ErrorCode.COURSE_RUBRIC_REQUIRED,
                    "Hãy hoàn thiện và DUYỆT Rubric của môn (Bước 2) trước khi sinh ngân hàng câu hỏi");
        }
        DifficultyRatioConfig ratio = request.getDifficultyRatio();
        validateDifficultyRatio(ratio);

        int totalRequested = request.getChapters().stream().mapToInt(GenerateCourseQuestionsRequest.ChapterQuota::getCount).sum();
        if (totalRequested > 60) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Mỗi lần sinh tối đa 60 câu cho cả môn (đang chọn " + totalRequested + " câu)");
        }

        // Kiem tra truoc tat ca chuong de khong goi AI do dang roi moi bao loi
        List<Chapter> chapters = new ArrayList<>();
        List<List<Material>> chapterMaterials = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (GenerateCourseQuestionsRequest.ChapterQuota quota : request.getChapters()) {
            Chapter chapter = chapterRepository.findById(quota.getChapterId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.CHAPTER_NOT_FOUND,
                            "Không tìm thấy chương: " + quota.getChapterId()));
            if (!chapter.getCourse().getId().equals(course.getId())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Chương '" + chapter.getName() + "' không thuộc môn " + course.getCourseCode());
            }
            List<Material> ready = materialRepository.findByChapterIdOrderByCreatedAtDesc(chapter.getId()).stream()
                    .filter(m -> "READY".equalsIgnoreCase(m.getRagStatus()))
                    .toList();
            if (ready.isEmpty()) {
                missing.add(chapter.getName());
            }
            chapters.add(chapter);
            chapterMaterials.add(ready);
        }
        if (!missing.isEmpty()) {
            throw new BusinessException(ErrorCode.MATERIAL_NOT_READY,
                    "Các chương chưa có tài liệu sẵn sàng (READY): " + String.join(", ", missing)
                            + ". Hãy tải tài liệu lên ở Bước 1 hoặc bỏ chọn các chương này");
        }

        Rubric courseRubric = rubricRepository.findByCourse_Id(course.getId()).orElse(null);
        List<Question> allSaved = new ArrayList<>();
        for (int i = 0; i < chapters.size(); i++) {
            Chapter chapter = chapters.get(i);
            int total = request.getChapters().get(i).getCount();
            int easyCount = Math.round(total * ratio.getEasyPercent() / 100.0f);
            int mediumCount = Math.round(total * ratio.getMediumPercent() / 100.0f);
            int hardCount = total - easyCount - mediumCount;
            if (hardCount < 0) {
                hardCount = 0;
                mediumCount = total - easyCount;
            }

            List<GeneratedQuestionData> generated = invokeLlmQuestionGeneration(
                    course, chapter, chapterMaterials.get(i), total, easyCount, mediumCount, hardCount);

            List<Question> toSave = new ArrayList<>();
            for (GeneratedQuestionData data : generated) {
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
                toSave.add(q);
            }
            allSaved.addAll(questionRepository.saveAll(toSave));
            log.info("Saved {} AI-generated questions for chapter {} (course {})", toSave.size(), chapter.getId(), course.getId());
        }

        String actorName = principal.getEmail();
        if (principal.getId() != null) {
            actorName = userRepository.findById(principal.getId()).map(User::getFullName).orElse(principal.getEmail());
        }
        academicAuditLogService.logActivity(
                course.getId(),
                null,
                principal.getId(),
                actorName,
                AuditAction.AI_GENERATE_QUESTION,
                "QUESTION",
                course.getId(),
                Map.of("totalQuestions", allSaved.size(), "chapterCount", chapters.size())
        );

        return allSaved.stream().map(q -> contentMapper.toQuestionResponse(q, false)).toList();
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
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE,
                    "Hệ thống chưa bật AI (thiếu ANTHROPIC_API_KEY) nên chưa sinh được câu hỏi");
        }

        try {
            StringBuilder system = new StringBuilder();
            system.append("Bạn là chuyên gia khảo thí và giám khảo thi vấn đáp môn '")
                    .append(course.getName())
                    .append("'. Hãy tạo danh sách câu hỏi thi vấn đáp grounded 100% trên nội dung học liệu.\n")
                    .append("Quy tắc bắt buộc (BR-BANK-002, BR-BANK-004):\n")
                    .append("1. TUYỆT ĐỐI KHÔNG TẠO RUBRIC. Chỉ sinh nội dung câu hỏi, độ khó, đáp án mẫu, từ khoá.\n")
                    .append("2. Phân loại độ khó bắt buộc dùng đúng 3 giá trị: 'EASY', 'MEDIUM', 'HARD'.\n")
                    .append("3. expectedKeywords: từ 3 đến 6 từ khóa chuyên ngành bắt buộc.\n")
                    .append("4. Chỉ hỏi kiến thức có trong NỘI DUNG TÀI LIỆU được cung cấp; đáp án mẫu phải đúng theo tài liệu.\n")
                    .append("5. Câu hỏi ngắn, trả lời được bằng lời trong 1-2 phút, viết bằng tiếng Việt.");

            StringBuilder user = new StringBuilder();
            user.append("Môn học: ").append(course.getName()).append(" (").append(course.getCourseCode()).append(")\n");
            user.append("Chương: ").append(chapter.getName()).append("\n");
            if (chapter.getDescription() != null) {
                user.append("Mô tả chương: ").append(chapter.getDescription()).append("\n");
            }
            user.append(buildMaterialContext(materials));
            user.append("\nYêu cầu phân bổ số lượng câu hỏi:\n");
            user.append("- Tổng số câu hỏi: ").append(total).append("\n");
            user.append("- Số câu EASY (Dễ - nhận biết & định nghĩa): ").append(easyCount).append("\n");
            user.append("- Số câu MEDIUM (Trung bình - thông hiểu & vận dụng): ").append(mediumCount).append("\n");
            user.append("- Số câu HARD (Khó - phân tích sâu, so sánh & tình huống): ").append(hardCount).append("\n");
            user.append("\nTrả về JSON duy nhất theo schema: { \"questions\": [ { \"content\": \"...\", \"difficulty\": \"EASY|MEDIUM|HARD\", \"modelAnswer\": \"...\", \"expectedKeywords\": [\"...\"] } ] }");

            Map<String, Object> schema = Map.of(
                    "type", "OBJECT",
                    "properties", Map.of(
                            "questions", Map.of(
                                    "type", "ARRAY",
                                    "items", Map.of(
                                            "type", "OBJECT",
                                            "properties", Map.of(
                                                    "content", Map.of("type", "STRING"),
                                                    "difficulty", Map.of("type", "STRING", "enum", List.of("EASY", "MEDIUM", "HARD")),
                                                    "modelAnswer", Map.of("type", "STRING"),
                                                    "expectedKeywords", Map.of("type", "ARRAY", "items", Map.of("type", "STRING"))
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
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("LLM question generation failed: {}", ex.getMessage(), ex);
            throw new BusinessException(ErrorCode.AI_REQUEST_FAILED,
                    "AI không sinh được câu hỏi từ tài liệu: " + ex.getMessage());
        }

        throw new BusinessException(ErrorCode.AI_REQUEST_FAILED, "AI không trả về câu hỏi nào, vui lòng thử lại");
    }

    /**
     * Ngu canh cho AI: noi dung chu cua tung tai lieu (che do luu local).
     * Tai lieu khong co chu (che do Vertex AI RAG) thi chi liet ke ten file.
     * Tong noi dung bi cat o MAX_CONTEXT_CHARS de khong vuot gioi han prompt.
     */
    private String buildMaterialContext(List<Material> materials) {
        StringBuilder sb = new StringBuilder("Tài liệu tham chiếu:\n");
        int remaining = MAX_CONTEXT_CHARS;
        for (Material m : materials) {
            sb.append("\n=== TÀI LIỆU: ").append(m.getTitle()).append(" (File: ").append(m.getFileName()).append(") ===\n");
            String text = m.getContentText();
            if (text == null || text.isBlank()) {
                sb.append("(Không có nội dung chữ, chỉ dùng tên tài liệu làm gợi ý)\n");
                continue;
            }
            if (remaining <= 0) {
                sb.append("(Bỏ qua vì đã đủ độ dài ngữ cảnh)\n");
                continue;
            }
            String part = text.length() > remaining ? text.substring(0, remaining) + "\n...(đã cắt bớt)" : text;
            remaining -= Math.min(text.length(), remaining);
            sb.append(part).append('\n');
        }
        return sb.toString();
    }

    private record GeneratedQuestionData(
            String content,
            String difficulty,
            String modelAnswer,
            List<String> expectedKeywords
    ) {}
}
