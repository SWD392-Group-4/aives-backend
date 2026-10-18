package com.aives.modules.content.service.impl;

import com.aives.common.exception.BusinessException;
import com.aives.common.exception.ErrorCode;
import com.aives.common.security.UserPrincipal;
import com.aives.common.util.RandomIdGenerator;
import com.aives.modules.auth.enums.RoleName;
import com.aives.modules.content.dto.CreateLessonRequest;
import com.aives.modules.content.dto.CreateQuestionRequest;
import com.aives.modules.content.dto.CreateRubricRequest;
import com.aives.modules.content.dto.CreateTopicRequest;
import com.aives.modules.content.dto.LessonResponse;
import com.aives.modules.content.dto.QuestionFigureResponse;
import com.aives.modules.content.dto.QuestionResponse;
import com.aives.modules.content.dto.RubricCriterionRequest;
import com.aives.modules.content.dto.RubricResponse;
import com.aives.modules.content.dto.TopicResponse;
import com.aives.modules.content.dto.UpdateLessonRequest;
import com.aives.modules.content.dto.UpdateQuestionRequest;
import com.aives.modules.content.dto.UpdateRubricRequest;
import com.aives.modules.content.dto.UpdateTopicRequest;
import com.aives.modules.content.entity.Lesson;
import com.aives.modules.content.entity.Question;
import com.aives.modules.content.entity.QuestionFigure;
import com.aives.modules.content.entity.Rubric;
import com.aives.modules.content.entity.RubricCriterion;
import com.aives.modules.content.entity.Topic;
import com.aives.modules.content.mapper.ContentMapper;
import com.aives.modules.content.repository.LessonRepository;
import com.aives.modules.content.repository.QuestionFigureRepository;
import com.aives.modules.content.repository.QuestionRepository;
import com.aives.modules.content.repository.RubricCriterionRepository;
import com.aives.modules.content.repository.RubricRepository;
import com.aives.modules.content.repository.TopicRepository;
import com.aives.modules.content.service.ContentService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ContentServiceImpl implements ContentService {

    private static final String APPROVED = "APPROVED";
    private static final BigDecimal FULL_WEIGHT = new BigDecimal("100");
    private static final Set<String> VALID_BLOOM_LEVELS = Set.of("REMEMBER", "UNDERSTAND", "APPLY", "ANALYZE");
    private static final Set<String> VALID_QUESTION_STATUSES = Set.of("DRAFT", "APPROVED", "REJECTED", "ARCHIVED");

    private final LessonRepository lessonRepository;
    private final TopicRepository topicRepository;
    private final RubricRepository rubricRepository;
    private final RubricCriterionRepository criterionRepository;
    private final QuestionRepository questionRepository;
    private final QuestionFigureRepository figureRepository;
    private final ContentMapper contentMapper;
    private final RandomIdGenerator idGenerator;

    @Override
    @Transactional(readOnly = true)
    public List<LessonResponse> listLessons(UserPrincipal actor) {
        List<Lesson> lessons = visibleLessons(actor);
        List<String> lessonIds = idsOf(lessons);
        Map<String, Integer> topicCounts = new HashMap<>();
        Map<String, Integer> questionCounts = new HashMap<>();
        if (!lessonIds.isEmpty()) {
            for (Topic topic : topicRepository.findWithLessonByLessonIds(lessonIds)) {
                topicCounts.merge(topic.getLesson().getId(), 1, Integer::sum);
            }
            for (Question question : questionRepository.findWithTopicByLessonIds(lessonIds)) {
                questionCounts.merge(question.getTopic().getLesson().getId(), 1, Integer::sum);
            }
        }
        List<LessonResponse> result = new ArrayList<>();
        for (Lesson lesson : lessons) {
            result.add(LessonResponse.builder()
                    .id(lesson.getId())
                    .lecturerId(lesson.getLecturerId())
                    .title(lesson.getTitle())
                    .description(lesson.getDescription())
                    .topicCount(topicCounts.getOrDefault(lesson.getId(), 0))
                    .questionCount(questionCounts.getOrDefault(lesson.getId(), 0))
                    .createdAt(contentMapper.toInstant(lesson.getCreatedAt()))
                    .build());
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TopicResponse> listTopics(UserPrincipal actor, String lessonId) {
        List<String> lessonIds = idsOf(visibleLessons(actor));
        if (lessonIds.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> questionCounts = new HashMap<>();
        Map<String, Integer> approvedCounts = new HashMap<>();
        for (Question question : questionRepository.findWithTopicByLessonIds(lessonIds)) {
            questionCounts.merge(question.getTopic().getId(), 1, Integer::sum);
            if (APPROVED.equals(question.getStatus())) {
                approvedCounts.merge(question.getTopic().getId(), 1, Integer::sum);
            }
        }
        List<TopicResponse> result = new ArrayList<>();
        for (Topic topic : topicRepository.findWithLessonByLessonIds(lessonIds)) {
            if (lessonId == null || lessonId.isBlank() || lessonId.equals(topic.getLesson().getId())) {
                result.add(contentMapper.toTopicResponse(topic,
                        questionCounts.getOrDefault(topic.getId(), 0),
                        approvedCounts.getOrDefault(topic.getId(), 0)));
            }
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TopicResponse> getTopicsByIds(UserPrincipal actor, Collection<String> topicIds) {
        if (topicIds == null || topicIds.isEmpty()) {
            return List.of();
        }
        return listTopics(actor, null).stream()
                .filter(topic -> topicIds.contains(topic.getId()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> getTopicNames(Collection<String> topicIds) {
        Map<String, String> names = new HashMap<>();
        if (topicIds == null || topicIds.isEmpty()) {
            return names;
        }
        for (Topic topic : topicRepository.findAllById(topicIds)) {
            names.put(topic.getId(), topic.getName());
        }
        return names;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RubricResponse> listRubrics(UserPrincipal actor) {
        List<Rubric> rubrics = isAdmin(actor)
                ? rubricRepository.findAllByOrderByCreatedAtDescIdDesc()
                : rubricRepository.findByCreatedByOrderByCreatedAtDescIdDesc(actor.getId());
        if (rubrics.isEmpty()) {
            return List.of();
        }
        Map<String, List<RubricCriterion>> criteriaByRubric = new HashMap<>();
        List<String> rubricIds = rubrics.stream().map(Rubric::getId).toList();
        for (RubricCriterion criterion : criterionRepository.findByRubric_IdInOrderByOrderNoAsc(rubricIds)) {
            criteriaByRubric.computeIfAbsent(criterion.getRubric().getId(), key -> new ArrayList<>()).add(criterion);
        }
        List<RubricResponse> result = new ArrayList<>();
        for (Rubric rubric : rubrics) {
            result.add(contentMapper.toRubricResponse(rubric, criteriaByRubric.getOrDefault(rubric.getId(), List.of())));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuestionResponse> listQuestions(UserPrincipal actor, String topicId) {
        List<String> lessonIds = idsOf(visibleLessons(actor));
        if (lessonIds.isEmpty()) {
            return List.of();
        }
        List<Question> questions = new ArrayList<>();
        for (Question question : questionRepository.findWithTopicByLessonIds(lessonIds)) {
            if (topicId == null || topicId.isBlank() || topicId.equals(question.getTopic().getId())) {
                questions.add(question);
            }
        }
        Set<String> withFigure = questions.isEmpty() ? Set.of()
                : new HashSet<>(figureRepository.findQuestionIdsWithFigure(questions.stream().map(Question::getId).toList()));
        List<QuestionResponse> result = new ArrayList<>();
        for (Question question : questions) {
            result.add(contentMapper.toQuestionResponse(question, withFigure.contains(question.getId())));
        }
        return result;
    }

    @Override
    @Transactional
    public QuestionResponse changeQuestionStatus(UserPrincipal actor, String questionId, String status, String rubricId) {
        Question question = requireOwnQuestion(actor, questionId);

        if (rubricId != null && !rubricId.isBlank()) {
            Rubric rubric = requireOwnRubric(actor, rubricId.trim());
            question.setRubric(rubric);
        }

        if (APPROVED.equals(status)) {
            validateApprovedRubric(question.getRubric());
        }
        question.setStatus(status);
        return contentMapper.toQuestionResponse(questionRepository.save(question), figureRepository.existsById(questionId));
    }

    @Override
    @Transactional(readOnly = true)
    public QuestionFigureResponse getQuestionFigure(UserPrincipal actor, String questionId) {
        requireOwnQuestion(actor, questionId);
        QuestionFigure figure = figureRepository.findById(questionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.QUESTION_FIGURE_NOT_FOUND));
        return QuestionFigureResponse.builder()
                .questionId(questionId)
                .dataUrl(toDataUrl(figure))
                .width(figure.getWidth())
                .height(figure.getHeight())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> findQuestionFigureDataUrl(String questionId) {
        return figureRepository.findById(questionId).map(this::toDataUrl);
    }

    // ==================== MANUAL CRUD: LESSONS ====================

    @Override
    @Transactional
    public LessonResponse createLesson(UserPrincipal actor, CreateLessonRequest request) {
        Lesson lesson = Lesson.builder()
                .lecturerId(actor.getId())
                .title(request.getTitle().trim())
                .description(request.getDescription())
                .build();
        lesson.setId(idGenerator.next("LS"));
        Lesson saved = lessonRepository.save(lesson);
        return LessonResponse.builder()
                .id(saved.getId())
                .lecturerId(saved.getLecturerId())
                .title(saved.getTitle())
                .description(saved.getDescription())
                .topicCount(0)
                .questionCount(0)
                .createdAt(contentMapper.toInstant(saved.getCreatedAt()))
                .build();
    }

    @Override
    @Transactional
    public LessonResponse updateLesson(UserPrincipal actor, String lessonId, UpdateLessonRequest request) {
        Lesson lesson = requireOwnLesson(actor, lessonId);
        lesson.setTitle(request.getTitle().trim());
        lesson.setDescription(request.getDescription());
        Lesson saved = lessonRepository.save(lesson);

        int topicCount = (int) topicRepository.countByLesson_Id(lesson.getId());
        int questionCount = (int) questionRepository.countByTopic_Lesson_Id(lesson.getId());
        return LessonResponse.builder()
                .id(saved.getId())
                .lecturerId(saved.getLecturerId())
                .title(saved.getTitle())
                .description(saved.getDescription())
                .topicCount(topicCount)
                .questionCount(questionCount)
                .createdAt(contentMapper.toInstant(saved.getCreatedAt()))
                .build();
    }

    @Override
    @Transactional
    public void deleteLesson(UserPrincipal actor, String lessonId) {
        Lesson lesson = requireOwnLesson(actor, lessonId);
        if (topicRepository.existsByLesson_Id(lesson.getId())) {
            throw new BusinessException(ErrorCode.DATA_CONFLICT, "Không thể xoá bài học đang chứa chủ đề");
        }
        lessonRepository.delete(lesson);
        lessonRepository.flush();
    }

    // ==================== MANUAL CRUD: TOPICS ====================

    @Override
    @Transactional
    public TopicResponse createTopic(UserPrincipal actor, CreateTopicRequest request) {
        Lesson lesson = requireOwnLesson(actor, request.getLessonId().trim());
        Topic topic = Topic.builder()
                .lesson(lesson)
                .name(request.getName().trim())
                .description(request.getDescription())
                .build();
        topic.setId(idGenerator.next("TP"));
        Topic saved = topicRepository.save(topic);
        return contentMapper.toTopicResponse(saved, 0, 0);
    }

    @Override
    @Transactional
    public TopicResponse updateTopic(UserPrincipal actor, String topicId, UpdateTopicRequest request) {
        Topic topic = requireOwnTopic(actor, topicId);
        topic.setName(request.getName().trim());
        topic.setDescription(request.getDescription());
        Topic saved = topicRepository.save(topic);

        long questionCount = questionRepository.countByTopic_Id(topic.getId());
        long approvedCount = questionRepository.countByTopic_IdAndStatus(topic.getId(), APPROVED);
        return contentMapper.toTopicResponse(saved, (int) questionCount, (int) approvedCount);
    }

    @Override
    @Transactional
    public void deleteTopic(UserPrincipal actor, String topicId) {
        Topic topic = requireOwnTopic(actor, topicId);
        if (questionRepository.existsByTopic_Id(topic.getId())) {
            throw new BusinessException(ErrorCode.DATA_CONFLICT, "Không thể xoá chủ đề đang chứa câu hỏi");
        }
        topicRepository.delete(topic);
        topicRepository.flush();
    }

    // ==================== MANUAL CRUD: QUESTIONS ====================

    @Override
    @Transactional
    public QuestionResponse createQuestion(UserPrincipal actor, CreateQuestionRequest request) {
        Topic topic = requireOwnTopic(actor, request.getTopicId().trim());
        String bloom = normalizeBloomLevel(request.getBloomLevel());
        String status = normalizeQuestionStatus(request.getStatus());

        Rubric rubric = null;
        if (request.getRubricId() != null && !request.getRubricId().isBlank()) {
            rubric = requireOwnRubric(actor, request.getRubricId().trim());
        }

        if (APPROVED.equals(status)) {
            validateApprovedRubric(rubric);
        }

        Question question = Question.builder()
                .topic(topic)
                .rubric(rubric)
                .content(request.getContent().trim())
                .bloomLevel(bloom)
                .modelAnswer(request.getModelAnswer())
                .expectedKeywords(contentMapper.toKeywordsJson(request.getExpectedKeywords()))
                .source("MANUAL")
                .status(status)
                .build();
        question.setId(idGenerator.next("QS"));
        Question saved = questionRepository.save(question);
        return contentMapper.toQuestionResponse(saved, false);
    }

    @Override
    @Transactional
    public QuestionResponse updateQuestion(UserPrincipal actor, String questionId, UpdateQuestionRequest request) {
        Question question = requireOwnQuestion(actor, questionId);

        if (request.getTopicId() != null && !request.getTopicId().isBlank()) {
            Topic topic = requireOwnTopic(actor, request.getTopicId().trim());
            question.setTopic(topic);
        }

        if (request.getRubricId() != null) {
            if (request.getRubricId().isBlank()) {
                question.setRubric(null);
            } else {
                Rubric rubric = requireOwnRubric(actor, request.getRubricId().trim());
                question.setRubric(rubric);
            }
        }

        if (request.getContent() != null && !request.getContent().isBlank()) {
            question.setContent(request.getContent().trim());
        }
        if (request.getBloomLevel() != null && !request.getBloomLevel().isBlank()) {
            question.setBloomLevel(normalizeBloomLevel(request.getBloomLevel()));
        }
        if (request.getModelAnswer() != null) {
            question.setModelAnswer(request.getModelAnswer());
        }
        if (request.getExpectedKeywords() != null) {
            question.setExpectedKeywords(contentMapper.toKeywordsJson(request.getExpectedKeywords()));
        }
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            question.setStatus(normalizeQuestionStatus(request.getStatus()));
        }

        if (APPROVED.equals(question.getStatus())) {
            validateApprovedRubric(question.getRubric());
        }

        Question saved = questionRepository.save(question);
        boolean hasFigure = figureRepository.existsById(questionId);
        return contentMapper.toQuestionResponse(saved, hasFigure);
    }

    @Override
    @Transactional
    public void deleteQuestion(UserPrincipal actor, String questionId) {
        Question question = requireOwnQuestion(actor, questionId);
        if (figureRepository.existsById(questionId)) {
            figureRepository.deleteById(questionId);
        }
        questionRepository.delete(question);
        questionRepository.flush();
    }

    // ==================== MANUAL CRUD: RUBRICS & CRITERIA ====================

    @Override
    @Transactional
    public RubricResponse createRubric(UserPrincipal actor, CreateRubricRequest request) {
        validateRubricCriteriaWeights(request.getCriteria());

        Rubric rubric = Rubric.builder()
                .createdBy(actor.getId())
                .name(request.getName().trim())
                .description(request.getDescription())
                .maxScore(request.getMaxScore() != null ? request.getMaxScore() : new BigDecimal("10.00"))
                .build();
        rubric.setId(idGenerator.next("RB"));
        Rubric savedRubric = rubricRepository.save(rubric);

        List<RubricCriterion> criteria = new ArrayList<>();
        int orderNo = 1;
        for (RubricCriterionRequest cReq : request.getCriteria()) {
            RubricCriterion criterion = RubricCriterion.builder()
                    .rubric(savedRubric)
                    .name(cReq.getName().trim())
                    .description(cReq.getDescription().trim())
                    .weightPercent(cReq.getWeightPercent())
                    .orderNo(orderNo++)
                    .build();
            criterion.setId(idGenerator.next("RC"));
            criteria.add(criterion);
        }
        criterionRepository.saveAll(criteria);
        return contentMapper.toRubricResponse(savedRubric, criteria);
    }

    @Override
    @Transactional
    public RubricResponse updateRubric(UserPrincipal actor, String rubricId, UpdateRubricRequest request) {
        Rubric rubric = requireOwnRubric(actor, rubricId);
        validateRubricCriteriaWeights(request.getCriteria());

        rubric.setName(request.getName().trim());
        rubric.setDescription(request.getDescription());
        if (request.getMaxScore() != null) {
            rubric.setMaxScore(request.getMaxScore());
        }
        Rubric savedRubric = rubricRepository.save(rubric);

        List<RubricCriterion> existingCriteria = criterionRepository.findByRubric_IdOrderByOrderNoAsc(rubricId);
        criterionRepository.deleteAll(existingCriteria);

        List<RubricCriterion> criteria = new ArrayList<>();
        int orderNo = 1;
        for (RubricCriterionRequest cReq : request.getCriteria()) {
            RubricCriterion criterion = RubricCriterion.builder()
                    .rubric(savedRubric)
                    .name(cReq.getName().trim())
                    .description(cReq.getDescription().trim())
                    .weightPercent(cReq.getWeightPercent())
                    .orderNo(orderNo++)
                    .build();
            criterion.setId(idGenerator.next("RC"));
            criteria.add(criterion);
        }
        criterionRepository.saveAll(criteria);
        return contentMapper.toRubricResponse(savedRubric, criteria);
    }

    @Override
    @Transactional
    public void deleteRubric(UserPrincipal actor, String rubricId) {
        Rubric rubric = requireOwnRubric(actor, rubricId);
        if (questionRepository.existsByRubric_Id(rubric.getId())) {
            throw new BusinessException(ErrorCode.DATA_CONFLICT, "Không thể xoá rubric đang được sử dụng bởi câu hỏi");
        }
        criterionRepository.deleteAll(criterionRepository.findByRubric_IdOrderByOrderNoAsc(rubric.getId()));
        rubricRepository.delete(rubric);
        rubricRepository.flush();
    }

    // ==================== HELPER METHODS ====================

    private String toDataUrl(QuestionFigure figure) {
        return "data:" + figure.getContentType() + ";base64," + Base64.getEncoder().encodeToString(figure.getImageData());
    }

    private Lesson requireOwnLesson(UserPrincipal actor, String lessonId) {
        return lessonRepository.findById(lessonId)
                .filter(found -> isAdmin(actor) || found.getLecturerId().equals(actor.getId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.LESSON_NOT_FOUND));
    }

    private Topic requireOwnTopic(UserPrincipal actor, String topicId) {
        return topicRepository.findWithLessonById(topicId)
                .filter(found -> isAdmin(actor) || found.getLesson().getLecturerId().equals(actor.getId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.TOPIC_NOT_FOUND));
    }

    private Rubric requireOwnRubric(UserPrincipal actor, String rubricId) {
        return rubricRepository.findById(rubricId)
                .filter(found -> isAdmin(actor) || found.getCreatedBy().equals(actor.getId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.RUBRIC_NOT_FOUND));
    }

    /** Cau hoi cua giang vien khac cung bao "khong tim thay", khong de lo la cau hoi do co ton tai. */
    private Question requireOwnQuestion(UserPrincipal actor, String questionId) {
        return questionRepository.findWithTopicById(questionId)
                .filter(found -> isAdmin(actor) || found.getTopic().getLesson().getLecturerId().equals(actor.getId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.QUESTION_NOT_FOUND));
    }

    private void validateApprovedRubric(Rubric rubric) {
        // BR-BANK-001: cau hoi duoc duyet phai co rubric, va rubric phai du 100% trong so
        if (rubric == null) {
            throw new BusinessException(ErrorCode.QUESTION_RUBRIC_REQUIRED);
        }
        BigDecimal totalWeight = criterionRepository.findByRubric_IdOrderByOrderNoAsc(rubric.getId())
                .stream()
                .map(RubricCriterion::getWeightPercent)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalWeight.compareTo(FULL_WEIGHT) != 0) {
            throw new BusinessException(ErrorCode.QUESTION_RUBRIC_REQUIRED,
                    "Rubric của câu hỏi chưa hợp lệ: tổng trọng số các tiêu chí phải bằng 100% (hiện là "
                            + totalWeight.stripTrailingZeros().toPlainString() + "%)");
        }
    }

    private void validateRubricCriteriaWeights(List<RubricCriterionRequest> criteria) {
        if (criteria == null || criteria.isEmpty()) {
            throw new BusinessException(ErrorCode.RUBRIC_WEIGHT_INVALID, "Rubric phải có ít nhất một tiêu chí");
        }
        BigDecimal totalWeight = criteria.stream()
                .map(RubricCriterionRequest::getWeightPercent)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalWeight.compareTo(FULL_WEIGHT) != 0) {
            throw new BusinessException(ErrorCode.RUBRIC_WEIGHT_INVALID);
        }
    }

    private String normalizeBloomLevel(String bloomLevel) {
        if (bloomLevel == null || bloomLevel.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Cấp độ Bloom không được để trống");
        }
        String normalized = bloomLevel.trim().toUpperCase(Locale.ROOT);
        if (!VALID_BLOOM_LEVELS.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Cấp độ Bloom không hợp lệ: phải là REMEMBER, UNDERSTAND, APPLY hoặc ANALYZE");
        }
        return normalized;
    }

    private String normalizeQuestionStatus(String status) {
        if (status == null || status.isBlank()) {
            return "DRAFT";
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!VALID_QUESTION_STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Trạng thái câu hỏi không hợp lệ");
        }
        return normalized;
    }

    private List<Lesson> visibleLessons(UserPrincipal actor) {
        return isAdmin(actor)
                ? lessonRepository.findAllByOrderByCreatedAtDescIdDesc()
                : lessonRepository.findByLecturerIdOrderByCreatedAtDescIdDesc(actor.getId());
    }

    private List<String> idsOf(List<Lesson> lessons) {
        return lessons.stream().map(Lesson::getId).toList();
    }

    private boolean isAdmin(UserPrincipal actor) {
        return actor.getRole() == RoleName.ADMIN;
    }
}

