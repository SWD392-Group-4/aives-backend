package com.aives.modules.content.service.impl;

import com.aives.common.dto.PageResponse;
import com.aives.common.exception.BusinessException;
import com.aives.common.exception.ErrorCode;
import com.aives.common.security.UserPrincipal;
import com.aives.common.util.RandomIdGenerator;
import com.aives.modules.auth.entity.User;
import com.aives.modules.auth.repository.UserRepository;
import com.aives.modules.content.dto.CreateChapterQuestionRequest;
import com.aives.modules.content.dto.QuestionResponse;
import com.aives.modules.content.dto.UpdateQuestionRequest;
import com.aives.modules.content.entity.Chapter;
import com.aives.modules.content.entity.Course;
import com.aives.modules.content.entity.Question;
import com.aives.modules.content.entity.Rubric;
import com.aives.modules.content.enums.AuditAction;
import com.aives.modules.content.enums.QuestionDifficulty;
import com.aives.modules.content.enums.QuestionSource;
import com.aives.modules.content.enums.QuestionStatus;
import com.aives.modules.content.mapper.ContentMapper;
import com.aives.modules.content.repository.ChapterRepository;
import com.aives.modules.content.repository.QuestionFigureRepository;
import com.aives.modules.content.repository.QuestionRepository;
import com.aives.modules.content.repository.RubricRepository;
import com.aives.modules.content.service.AcademicAuditLogService;
import com.aives.modules.content.service.CourseRubricService;
import com.aives.modules.content.service.QuestionBankService;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionBankServiceImpl implements QuestionBankService {

    private final ChapterRepository chapterRepository;
    private final com.aives.modules.content.repository.CourseRepository courseRepository;
    private final QuestionRepository questionRepository;
    private final QuestionFigureRepository questionFigureRepository;
    private final RubricRepository rubricRepository;
    private final CourseRubricService courseRubricService;
    private final UserRepository userRepository;
    private final AcademicAuditLogService academicAuditLogService;
    private final ContentMapper contentMapper;
    private final RandomIdGenerator idGenerator;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<QuestionResponse> getQuestions(
            String chapterId, String status, String difficulty, Pageable pageable) {
        if (!chapterRepository.existsById(chapterId)) {
            throw new BusinessException(ErrorCode.CHAPTER_NOT_FOUND, "Không tìm thấy chương: " + chapterId);
        }

        Specification<Question> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("chapter").get("id"), chapterId));

            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("status")), status.trim().toUpperCase()));
            }
            if (difficulty != null && !difficulty.isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("difficulty")), difficulty.trim().toUpperCase()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return toPage(questionRepository.findAll(spec, pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<QuestionResponse> getCourseQuestions(
            String courseId, String chapterId, String status, String difficulty, Pageable pageable) {
        if (!courseRepository.existsById(courseId)) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND, "Không tìm thấy môn học: " + courseId);
        }

        Specification<Question> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("chapter").get("course").get("id"), courseId));
            if (chapterId != null && !chapterId.isBlank()) {
                predicates.add(cb.equal(root.get("chapter").get("id"), chapterId));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("status")), status.trim().toUpperCase()));
            }
            if (difficulty != null && !difficulty.isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("difficulty")), difficulty.trim().toUpperCase()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return toPage(questionRepository.findAll(spec, pageable));
    }

    private PageResponse<QuestionResponse> toPage(Page<Question> page) {
        List<String> questionIds = page.getContent().stream().map(Question::getId).toList();
        Set<String> figureIds = questionIds.isEmpty()
                ? Set.of()
                : new HashSet<>(questionFigureRepository.findQuestionIdsWithFigure(questionIds));

        List<QuestionResponse> items = page.getContent().stream()
                .map(q -> contentMapper.toQuestionResponse(q, figureIds.contains(q.getId())))
                .toList();

        return new PageResponse<>(
                items,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    @Override
    @Transactional
    public QuestionResponse createManualQuestion(
            String chapterId, CreateChapterQuestionRequest request, UserPrincipal principal) {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAPTER_NOT_FOUND, "Không tìm thấy chương: " + chapterId));

        Rubric courseRubric = rubricRepository.findByCourse_Id(chapter.getCourse().getId()).orElse(null);

        Question question = Question.builder()
                .chapter(chapter)
                .rubric(courseRubric)
                .content(request.getContent().trim())
                .difficulty(request.getDifficulty() != null ? request.getDifficulty().name() : QuestionDifficulty.MEDIUM.name())
                .modelAnswer(request.getModelAnswer())
                .expectedKeywords(contentMapper.toKeywordsJson(request.getExpectedKeywords()))
                .source(QuestionSource.MANUAL.name())
                .status(QuestionStatus.DRAFT.name())
                .createdBy(principal.getId())
                .build();
        question.setId(idGenerator.next("QS"));

        Question saved = questionRepository.save(question);
        log.info("Created manual question: id={}, chapterId={}, courseId={}",
                saved.getId(), chapter.getId(), chapter.getCourse().getId());

        String actorName = resolveActorName(principal);
        academicAuditLogService.logActivity(
                chapter.getCourse().getId(),
                chapter.getId(),
                principal.getId(),
                actorName,
                AuditAction.CREATE_QUESTION,
                "QUESTION",
                saved.getId(),
                Map.of("difficulty", saved.getDifficulty(), "source", saved.getSource())
        );

        return contentMapper.toQuestionResponse(saved, false);
    }

    @Override
    @Transactional(readOnly = true)
    public QuestionResponse getQuestionDetail(String questionId) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.QUESTION_NOT_FOUND, "Không tìm thấy câu hỏi: " + questionId));

        boolean hasFigure = !questionFigureRepository.findQuestionIdsWithFigure(List.of(questionId)).isEmpty();
        return contentMapper.toQuestionResponse(question, hasFigure);
    }

    @Override
    @Transactional
    public QuestionResponse updateQuestion(
            String questionId, UpdateQuestionRequest request, UserPrincipal principal) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.QUESTION_NOT_FOUND, "Không tìm thấy câu hỏi: " + questionId));

        if (request.getContent() != null && !request.getContent().isBlank()) {
            question.setContent(request.getContent().trim());
        }
        if (request.getDifficulty() != null && !request.getDifficulty().isBlank()) {
            question.setDifficulty(QuestionDifficulty.fromString(request.getDifficulty()).name());
        }
        if (request.getModelAnswer() != null) {
            question.setModelAnswer(request.getModelAnswer());
        }
        if (request.getExpectedKeywords() != null) {
            question.setExpectedKeywords(contentMapper.toKeywordsJson(request.getExpectedKeywords()));
        }

        Question saved = questionRepository.save(question);
        log.info("Updated question: id={}", saved.getId());

        String courseId = getCourseId(saved);
        String chapterId = saved.getChapter() != null ? saved.getChapter().getId() : null;
        String actorName = resolveActorName(principal);

        academicAuditLogService.logActivity(
                courseId,
                chapterId,
                principal.getId(),
                actorName,
                AuditAction.UPDATE_QUESTION,
                "QUESTION",
                saved.getId(),
                Map.of("difficulty", saved.getDifficulty(), "status", saved.getStatus())
        );

        boolean hasFigure = !questionFigureRepository.findQuestionIdsWithFigure(List.of(questionId)).isEmpty();
        return contentMapper.toQuestionResponse(saved, hasFigure);
    }

    @Override
    @Transactional
    public void deleteQuestion(String questionId, UserPrincipal principal) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.QUESTION_NOT_FOUND, "Không tìm thấy câu hỏi: " + questionId));

        String courseId = getCourseId(question);
        String chapterId = question.getChapter() != null ? question.getChapter().getId() : null;
        String actorName = resolveActorName(principal);

        questionRepository.delete(question);
        log.info("Deleted question: id={}", questionId);

        academicAuditLogService.logActivity(
                courseId,
                chapterId,
                principal.getId(),
                actorName,
                AuditAction.DELETE_QUESTION,
                "QUESTION",
                questionId,
                Map.of("deleted", true)
        );
    }

    @Override
    @Transactional
    public QuestionResponse approveQuestion(String questionId, UserPrincipal principal) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.QUESTION_NOT_FOUND, "Không tìm thấy câu hỏi: " + questionId));

        String courseId = getCourseId(question);
        if (courseId == null) {
            throw new BusinessException(ErrorCode.COURSE_RUBRIC_REQUIRED,
                    "Không thể duyệt câu hỏi vì câu hỏi chưa được liên kết với môn học");
        }

        // BR-BANK-001: Môn học bắt buộc phải có Rubric 100% hợp lệ trước khi duyệt câu hỏi
        if (!courseRubricService.isRubricReadyForGrading(courseId)) {
            throw new BusinessException(ErrorCode.COURSE_RUBRIC_REQUIRED,
                    "Rubric của môn học phải đủ 100% và đã được duyệt trước khi duyệt câu hỏi (BR-BANK-001)");
        }

        question.setStatus(QuestionStatus.APPROVED.name());
        Question saved = questionRepository.save(question);
        log.info("Approved question: id={}, courseId={}", saved.getId(), courseId);

        String chapterId = saved.getChapter() != null ? saved.getChapter().getId() : null;
        String actorName = resolveActorName(principal);

        academicAuditLogService.logActivity(
                courseId,
                chapterId,
                principal.getId(),
                actorName,
                AuditAction.APPROVE_QUESTION,
                "QUESTION",
                saved.getId(),
                Map.of("status", QuestionStatus.APPROVED.name())
        );

        boolean hasFigure = !questionFigureRepository.findQuestionIdsWithFigure(List.of(questionId)).isEmpty();
        return contentMapper.toQuestionResponse(saved, hasFigure);
    }

    @Override
    @Transactional
    public QuestionResponse rejectQuestion(String questionId, String reason, UserPrincipal principal) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.QUESTION_NOT_FOUND, "Không tìm thấy câu hỏi: " + questionId));

        question.setStatus(QuestionStatus.REJECTED.name());
        Question saved = questionRepository.save(question);
        log.info("Rejected question: id={}, reason={}", saved.getId(), reason);

        String courseId = getCourseId(saved);
        String chapterId = saved.getChapter() != null ? saved.getChapter().getId() : null;
        String actorName = resolveActorName(principal);

        academicAuditLogService.logActivity(
                courseId,
                chapterId,
                principal.getId(),
                actorName,
                AuditAction.REJECT_QUESTION,
                "QUESTION",
                saved.getId(),
                Map.of("status", QuestionStatus.REJECTED.name(), "reason", reason != null ? reason : "")
        );

        boolean hasFigure = !questionFigureRepository.findQuestionIdsWithFigure(List.of(questionId)).isEmpty();
        return contentMapper.toQuestionResponse(saved, hasFigure);
    }

    private String getCourseId(Question question) {
        if (question.getChapter() != null && question.getChapter().getCourse() != null) {
            return question.getChapter().getCourse().getId();
        }
        if (question.getRubric() != null && question.getRubric().getCourse() != null) {
            return question.getRubric().getCourse().getId();
        }
        return null;
    }

    private String resolveActorName(UserPrincipal principal) {
        if (principal == null) {
            return "SYSTEM";
        }
        if (principal.getId() != null) {
            return userRepository.findById(principal.getId())
                    .map(User::getFullName)
                    .orElse(principal.getEmail());
        }
        return principal.getEmail();
    }
}
