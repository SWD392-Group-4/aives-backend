package com.aives.modules.content.service.impl;

import com.aives.common.exception.BusinessException;
import com.aives.common.exception.ErrorCode;
import com.aives.common.util.RandomIdGenerator;
import com.aives.modules.auth.entity.User;
import com.aives.modules.auth.repository.UserRepository;
import com.aives.modules.content.document.AcademicAuditLog;
import com.aives.modules.content.dto.CreateQuestionRequest;
import com.aives.modules.content.dto.QuestionFilter;
import com.aives.modules.content.dto.QuestionResponse;
import com.aives.modules.content.dto.UpdateQuestionRequest;
import com.aives.modules.content.entity.Chapter;
import com.aives.modules.content.entity.Question;
import com.aives.modules.content.enums.AuditAction;
import com.aives.modules.content.enums.QuestionSource;
import com.aives.modules.content.enums.QuestionStatus;
import com.aives.modules.content.repository.ChapterRepository;
import com.aives.modules.content.repository.QuestionRepository;
import com.aives.modules.content.service.IAcademicAuditLogService;
import com.aives.modules.content.service.ICourseRubricService;
import com.aives.modules.content.service.IQuestionBankService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
public class QuestionBankServiceImpl implements IQuestionBankService {

    private final ChapterRepository chapterRepository;
    private final QuestionRepository questionRepository;
    private final UserRepository userRepository;
    private final ICourseRubricService courseRubricService;
    private final IAcademicAuditLogService auditLogService;
    private final RandomIdGenerator idGenerator;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<QuestionResponse> getQuestions(String chapterId, QuestionFilter filter, Pageable pageable) {
        log.info("Lay danh sach cau hoi trong chuong: chapterId={}, status={}, difficulty={}",
                chapterId, filter.getStatus(), filter.getDifficulty());

        if (!chapterRepository.existsById(chapterId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy chương " + chapterId);
        }

        Specification<Question> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("chapter").get("id"), chapterId));

            if (filter.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), filter.getStatus()));
            }
            if (filter.getDifficulty() != null) {
                predicates.add(cb.equal(root.get("difficulty"), filter.getDifficulty()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Question> page = questionRepository.findAll(spec, pageable);
        return page.map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public QuestionResponse getQuestionById(String questionId) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.QUESTION_NOT_FOUND, "Không tìm thấy câu hỏi " + questionId));
        return mapToResponse(question);
    }

    @Override
    @Transactional
    public QuestionResponse createManualQuestion(String chapterId, CreateQuestionRequest request, String lecturerId) {
        log.info("Soan cau hoi thu cong vao ngan hang: chapterId={}, lecturerId={}", chapterId, lecturerId);
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy chương " + chapterId));

        User lecturer = userRepository.findById(lecturerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy giảng viên " + lecturerId));

        String keywordsJson = "[]";
        try {
            if (request.getExpectedKeywords() != null) {
                keywordsJson = objectMapper.writeValueAsString(request.getExpectedKeywords());
            }
        } catch (Exception ignored) {}

        Question question = Question.builder()
                .chapter(chapter)
                .createdBy(lecturer)
                .content(request.getContent())
                .difficulty(request.getDifficulty())
                .modelAnswer(request.getModelAnswer())
                .expectedKeywords(keywordsJson)
                .source(QuestionSource.MANUAL)
                .status(QuestionStatus.DRAFT)
                .build();
        question.setId(idGenerator.next("QU"));

        question = questionRepository.save(question);

        // Ghi Audit Log MongoDB
        Map<String, Object> details = new HashMap<>();
        details.put("content", question.getContent());
        details.put("difficulty", question.getDifficulty().name());

        auditLogService.logActivity(AcademicAuditLog.builder()
                .courseId(chapter.getCourse().getId())
                .chapterId(chapterId)
                .actorId(lecturerId)
                .actorName(lecturer.getFullName())
                .action(AuditAction.CREATE_QUESTION)
                .targetEntity("QUESTION")
                .targetId(question.getId())
                .details(details)
                .build());

        return mapToResponse(question);
    }

    @Override
    @Transactional
    public QuestionResponse updateQuestion(String questionId, UpdateQuestionRequest request, String lecturerId) {
        log.info("Cap nhat cau hoi: questionId={}, lecturerId={}", questionId, lecturerId);
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.QUESTION_NOT_FOUND, "Không tìm thấy câu hỏi " + questionId));

        User lecturer = userRepository.findById(lecturerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy giảng viên " + lecturerId));

        Map<String, Object> beforeState = new HashMap<>();
        beforeState.put("content", question.getContent());
        beforeState.put("difficulty", question.getDifficulty().name());
        beforeState.put("modelAnswer", question.getModelAnswer());

        String keywordsJson = "[]";
        try {
            if (request.getExpectedKeywords() != null) {
                keywordsJson = objectMapper.writeValueAsString(request.getExpectedKeywords());
            }
        } catch (Exception ignored) {}

        question.setContent(request.getContent());
        question.setDifficulty(request.getDifficulty());
        question.setModelAnswer(request.getModelAnswer());
        question.setExpectedKeywords(keywordsJson);

        question = questionRepository.save(question);

        Map<String, Object> afterState = new HashMap<>();
        afterState.put("content", question.getContent());
        afterState.put("difficulty", question.getDifficulty().name());
        afterState.put("modelAnswer", question.getModelAnswer());

        Map<String, Object> details = new HashMap<>();
        details.put("before_state", beforeState);
        details.put("after_state", afterState);

        auditLogService.logActivity(AcademicAuditLog.builder()
                .courseId(question.getChapter().getCourse().getId())
                .chapterId(question.getChapter().getId())
                .actorId(lecturerId)
                .actorName(lecturer.getFullName())
                .action(AuditAction.UPDATE_QUESTION)
                .targetEntity("QUESTION")
                .targetId(question.getId())
                .details(details)
                .build());

        return mapToResponse(question);
    }

    @Override
    @Transactional
    public QuestionResponse approveQuestion(String questionId, String lecturerId) {
        log.info("Phe duyet cau hoi (DRAFT -> APPROVED): questionId={}, lecturerId={}", questionId, lecturerId);
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.QUESTION_NOT_FOUND, "Không tìm thấy câu hỏi " + questionId));

        User lecturer = userRepository.findById(lecturerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy giảng viên " + lecturerId));

        // KIEM TRA INVARIANT BR-BANK-001: Mon hoc bat buoc phai co Rubric 100% hoan chinh moi cho phep phe duyet
        String courseId = question.getChapter().getCourse().getId();
        if (!courseRubricService.isRubricReadyForGrading(courseId)) {
            log.warn("Chan phe duyet cau hoi do mon hoc chua co Rubric 100%: courseId={}, questionId={}", courseId, questionId);
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "Không thể phê duyệt câu hỏi: Môn học " + courseId + " chưa có bộ Rubric hoàn chỉnh đạt tổng trọng số 100% (BR-BANK-001)");
        }

        Map<String, Object> beforeState = Map.of("status", question.getStatus().name());

        question.setStatus(QuestionStatus.APPROVED);
        question = questionRepository.save(question);

        Map<String, Object> afterState = Map.of("status", question.getStatus().name());
        Map<String, Object> details = new HashMap<>();
        details.put("before_state", beforeState);
        details.put("after_state", afterState);

        auditLogService.logActivity(AcademicAuditLog.builder()
                .courseId(courseId)
                .chapterId(question.getChapter().getId())
                .actorId(lecturerId)
                .actorName(lecturer.getFullName())
                .action(AuditAction.APPROVE_QUESTION)
                .targetEntity("QUESTION")
                .targetId(question.getId())
                .details(details)
                .build());

        return mapToResponse(question);
    }

    @Override
    @Transactional
    public QuestionResponse rejectQuestion(String questionId, String reason, String lecturerId) {
        log.info("Tu choi cau hoi (DRAFT -> REJECTED): questionId={}, reason={}, lecturerId={}", questionId, reason, lecturerId);
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.QUESTION_NOT_FOUND, "Không tìm thấy câu hỏi " + questionId));

        User lecturer = userRepository.findById(lecturerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy giảng viên " + lecturerId));

        Map<String, Object> beforeState = Map.of("status", question.getStatus().name());

        question.setStatus(QuestionStatus.REJECTED);
        question = questionRepository.save(question);

        Map<String, Object> afterState = Map.of("status", question.getStatus().name());
        Map<String, Object> details = new HashMap<>();
        details.put("before_state", beforeState);
        details.put("after_state", afterState);
        details.put("reason", reason != null ? reason : "");

        auditLogService.logActivity(AcademicAuditLog.builder()
                .courseId(question.getChapter().getCourse().getId())
                .chapterId(question.getChapter().getId())
                .actorId(lecturerId)
                .actorName(lecturer.getFullName())
                .action(AuditAction.REJECT_QUESTION)
                .targetEntity("QUESTION")
                .targetId(question.getId())
                .details(details)
                .build());

        return mapToResponse(question);
    }

    @Override
    @Transactional
    public void deleteQuestion(String questionId, String lecturerId) {
        log.info("Xoa cau hoi: questionId={}, lecturerId={}", questionId, lecturerId);
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.QUESTION_NOT_FOUND, "Không tìm thấy câu hỏi " + questionId));

        User lecturer = userRepository.findById(lecturerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy giảng viên " + lecturerId));

        String courseId = question.getChapter().getCourse().getId();
        String chapterId = question.getChapter().getId();

        Map<String, Object> details = new HashMap<>();
        details.put("deletedContent", question.getContent());
        details.put("deletedStatus", question.getStatus().name());

        questionRepository.delete(question);

        auditLogService.logActivity(AcademicAuditLog.builder()
                .courseId(courseId)
                .chapterId(chapterId)
                .actorId(lecturerId)
                .actorName(lecturer.getFullName())
                .action(AuditAction.DELETE_QUESTION)
                .targetEntity("QUESTION")
                .targetId(questionId)
                .details(details)
                .build());
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
}