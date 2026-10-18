package com.aives.modules.content.service.impl;

import com.aives.common.exception.BusinessException;
import com.aives.common.exception.ErrorCode;
import com.aives.common.security.UserPrincipal;
import com.aives.modules.auth.enums.RoleName;
import com.aives.modules.content.dto.LessonResponse;
import com.aives.modules.content.dto.QuestionFigureResponse;
import com.aives.modules.content.dto.QuestionResponse;
import com.aives.modules.content.dto.RubricResponse;
import com.aives.modules.content.dto.TopicResponse;
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
import java.util.Map;
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

    private final LessonRepository lessonRepository;
    private final TopicRepository topicRepository;
    private final RubricRepository rubricRepository;
    private final RubricCriterionRepository criterionRepository;
    private final QuestionRepository questionRepository;
    private final QuestionFigureRepository figureRepository;
    private final ContentMapper contentMapper;

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
            Rubric rubric = rubricRepository.findById(rubricId.trim())
                    .filter(found -> isAdmin(actor) || found.getCreatedBy().equals(actor.getId()))
                    .orElseThrow(() -> new BusinessException(ErrorCode.RUBRIC_NOT_FOUND));
            question.setRubric(rubric);
        }

        if (APPROVED.equals(status)) {
            // BR-BANK-001: cau hoi duoc duyet phai co rubric, va rubric phai du 100% trong so
            if (question.getRubric() == null) {
                throw new BusinessException(ErrorCode.QUESTION_RUBRIC_REQUIRED);
            }
            BigDecimal totalWeight = criterionRepository.findByRubric_IdOrderByOrderNoAsc(question.getRubric().getId())
                    .stream()
                    .map(RubricCriterion::getWeightPercent)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (totalWeight.compareTo(FULL_WEIGHT) != 0) {
                throw new BusinessException(ErrorCode.QUESTION_RUBRIC_REQUIRED,
                        "Rubric của câu hỏi chưa hợp lệ: tổng trọng số các tiêu chí phải bằng 100% (hiện là "
                                + totalWeight.stripTrailingZeros().toPlainString() + "%)");
            }
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

    private String toDataUrl(QuestionFigure figure) {
        return "data:" + figure.getContentType() + ";base64," + Base64.getEncoder().encodeToString(figure.getImageData());
    }

    /** Cau hoi cua giang vien khac cung bao "khong tim thay", khong de lo la cau hoi do co ton tai. */
    private Question requireOwnQuestion(UserPrincipal actor, String questionId) {
        return questionRepository.findWithTopicById(questionId)
                .filter(found -> isAdmin(actor) || found.getTopic().getLesson().getLecturerId().equals(actor.getId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.QUESTION_NOT_FOUND));
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
