package com.aives.modules.exam.service.impl;

import com.aives.common.dto.PageResponse;
import com.aives.common.exception.BusinessException;
import com.aives.common.exception.ErrorCode;
import com.aives.common.security.UserPrincipal;
import com.aives.common.util.RandomIdGenerator;
import com.aives.modules.auth.dto.UserResponse;
import com.aives.modules.auth.enums.RoleName;
import com.aives.modules.auth.service.UserService;
import com.aives.modules.content.dto.TopicResponse;
import com.aives.modules.content.entity.Chapter;
import com.aives.modules.content.entity.Course;
import com.aives.modules.content.entity.Question;
import com.aives.modules.content.repository.ChapterRepository;
import com.aives.modules.content.repository.CourseRepository;
import com.aives.modules.content.repository.QuestionRepository;
import com.aives.modules.content.service.ContentService;
import com.aives.modules.content.service.CourseRubricService;
import com.aives.modules.exam.dto.DeleteExamSessionResponse;
import com.aives.modules.exam.dto.ExamSessionRequest;
import com.aives.modules.exam.dto.ExamSessionResponse;
import com.aives.modules.exam.dto.LiveExamineeResponse;
import com.aives.modules.exam.dto.LiveExamMonitorResponse;
import com.aives.modules.exam.entity.VivaAttempt;
import com.aives.modules.exam.entity.VivaExam;
import com.aives.modules.exam.entity.VivaExamQuestion;
import com.aives.modules.exam.enums.AttemptStatus;
import com.aives.modules.exam.enums.ExamSessionStatus;
import com.aives.modules.exam.mapper.ExamMapper;
import com.aives.modules.exam.repository.VivaAttemptRepository;
import com.aives.modules.exam.repository.VivaExamQuestionRepository;
import com.aives.modules.exam.repository.VivaExamRepository;
import com.aives.modules.exam.service.ExamSessionService;
import com.aives.modules.exam.util.ExamCodeGenerator;
import com.aives.modules.vivaroom.entity.InterviewExchange;
import com.aives.modules.vivaroom.repository.InterviewExchangeRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ExamSessionServiceImpl implements ExamSessionService {

    private static final String EXAM_QUESTION_ID_PREFIX = "EQ";
    private static final String QUESTION_APPROVED = "APPROVED";

    // Phien chi duoc mo toi da 30 ngay
    private static final Duration MAX_WINDOW = Duration.ofDays(30);
    // Khong cho hen gio mo xa qua 365 ngay (thuong la nhap nham nam)
    private static final Duration MAX_START_AHEAD = Duration.ofDays(365);
    // Cho phep gio mo lech ve qua khu 1 phut (o chon gio tren FE chi chinh xac toi phut)
    private static final Duration START_PAST_TOLERANCE = Duration.ofMinutes(1);
    private static final int MAX_PAGE_SIZE = 100;
    private static final char LIKE_ESCAPE = '\\';

    private final VivaExamRepository examRepository;
    private final VivaAttemptRepository attemptRepository;
    private final VivaExamQuestionRepository examQuestionRepository;
    private final InterviewExchangeRepository exchangeRepository;
    private final ExamCodeGenerator codeGenerator;
    private final ExamMapper examMapper;
    private final UserService userService;
    private final ContentService contentService;
    private final CourseRepository courseRepository;
    private final ChapterRepository chapterRepository;
    private final QuestionRepository questionRepository;
    private final CourseRubricService courseRubricService;
    private final RandomIdGenerator idGenerator;
    private final Clock clock;

    @Override
    @Transactional
    public ExamSessionResponse createSession(ExamSessionRequest request, UserPrincipal actor) {
        LocalDateTime now = now();
        LocalDateTime startAt = toUtc(request.getStartAt());
        LocalDateTime endAt = toUtc(request.getEndAt());
        validateNewSchedule(startAt, endAt, request.getDurationMinutes(), now);

        String courseId = StringUtils.hasText(request.getCourseId()) ? request.getCourseId().trim() : null;
        List<Question> selectedQuestions = null;
        if (courseId != null) {
            selectedQuestions = validateCourseAndQuestions(courseId, request, actor);
        }

        Set<String> topicIds = normalizeTopicIds(request.getTopicIds());
        validateTopics(topicIds, request.getQuestionCount(), actor);

        Integer questionCount = request.getQuestionCount();
        if (selectedQuestions != null && !selectedQuestions.isEmpty()) {
            questionCount = selectedQuestions.size();
        }

        VivaExam exam = VivaExam.builder()
                .lecturerId(actor.getId())
                .courseId(courseId)
                .title(request.getTitle().trim())
                .description(normalizeDescription(request.getDescription()))
                .passcode(codeGenerator.generatePasscode())
                .durationMinutes(request.getDurationMinutes())
                .maxAttempts(request.getMaxAttempts() == null ? 1 : request.getMaxAttempts())
                .allowMobileExam(Boolean.TRUE.equals(request.getAllowMobileExam()))
                .questionCount(questionCount)
                .topicIds(topicIds)
                .startAt(startAt)
                .endAt(endAt)
                .build();
        exam.setId(codeGenerator.generateExamId());
        VivaExam savedExam = examRepository.save(exam);

        if (selectedQuestions != null && !selectedQuestions.isEmpty()) {
            saveExamQuestions(savedExam, selectedQuestions);
        }

        return toResponse(savedExam, now);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ExamSessionResponse> getSessions(
            UserPrincipal actor, String status, String keyword, int page, int size) {
        LocalDateTime now = now();
        ExamSessionStatus statusFilter = parseStatus(status);

        // LECTURER chi thay phien cua minh, ADMIN thay tat ca
        Specification<VivaExam> spec = (root, query, cb) -> cb.conjunction();
        if (actor.getRole() != RoleName.ADMIN) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("lecturerId"), actor.getId()));
        }
        if (StringUtils.hasText(keyword)) {
            String pattern = "%" + escapeLike(keyword.trim().toLowerCase(Locale.ROOT)) + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("title")), pattern, LIKE_ESCAPE),
                    cb.like(cb.lower(root.get("id")), pattern, LIKE_ESCAPE)));
        }
        if (statusFilter != null) {
            spec = spec.and(statusSpec(statusFilter, now));
        }

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "startAt").and(Sort.by(Sort.Direction.DESC, "createdAt")));
        Page<VivaExam> result = examRepository.findAll(spec, pageable);
        List<VivaExam> exams = result.getContent();

        // Lay ten nguoi tao va so luot thi cho ca trang trong 2 cau query
        Set<String> ownerIds = exams.stream().map(VivaExam::getLecturerId).collect(Collectors.toSet());
        Map<String, String> ownerNames = userService.getFullNamesByIds(ownerIds);
        Map<String, Long> attemptCounts = countAttempts(exams);
        Set<String> allTopicIds = exams.stream().flatMap(exam -> exam.getTopicIds().stream()).collect(Collectors.toSet());
        Map<String, String> topicNames = contentService.getTopicNames(allTopicIds);

        Set<String> courseIds = exams.stream()
                .map(VivaExam::getCourseId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        Map<String, String> courseNames = courseIds.isEmpty()
                ? Collections.emptyMap()
                : courseRepository.findAllById(courseIds).stream()
                        .collect(Collectors.toMap(Course::getId, Course::getName));

        List<ExamSessionResponse> items = exams.stream()
                .map(exam -> examMapper.toResponse(
                        exam,
                        ownerNames.get(exam.getLecturerId()),
                        attemptCounts.getOrDefault(exam.getId(), 0L),
                        topicNames,
                        exam.getCourseId() != null ? courseNames.get(exam.getCourseId()) : null,
                        null,
                        now))
                .toList();
        return new PageResponse<>(
                items, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public ExamSessionResponse getSession(String sessionId, UserPrincipal actor) {
        return toResponse(findAccessible(sessionId, actor), now());
    }

    @Override
    @Transactional
    public ExamSessionResponse updateSession(String sessionId, ExamSessionRequest request, UserPrincipal actor) {
        LocalDateTime now = now();
        VivaExam exam = findAccessible(sessionId, actor);
        LocalDateTime startAt = toUtc(request.getStartAt());
        LocalDateTime endAt = toUtc(request.getEndAt());
        // Khong gui maxAttempts thi giu nguyen gia tri dang co
        int maxAttempts = request.getMaxAttempts() == null ? exam.getMaxAttempts() : request.getMaxAttempts();

        String courseId = request.getCourseId() != null
                ? (StringUtils.hasText(request.getCourseId()) ? request.getCourseId().trim() : null)
                : exam.getCourseId();

        // Khong gui topicIds / questionCount thi giu nguyen gia tri dang co
        Set<String> topicIds = request.getTopicIds() == null
                ? new LinkedHashSet<>(exam.getTopicIds())
                : normalizeTopicIds(request.getTopicIds());
        Integer questionCount = request.getQuestionCount() == null ? exam.getQuestionCount() : request.getQuestionCount();

        boolean courseChanged = !Objects.equals(courseId, exam.getCourseId());
        boolean questionsChanged = courseChanged
                || !topicIds.equals(exam.getTopicIds())
                || !Objects.equals(questionCount, exam.getQuestionCount())
                || request.getQuestionIds() != null
                || request.getChapterIds() != null;

        List<Question> selectedQuestions = null;

        switch (examMapper.statusOf(exam, now)) {
            case CANCELLED -> throw new BusinessException(ErrorCode.EXAM_SESSION_CANCELLED,
                    "Phiên thi đã bị huỷ nên không thể chỉnh sửa");
            case ENDED -> throw new BusinessException(ErrorCode.EXAM_SESSION_ENDED,
                    "Phiên thi đã kết thúc nên không thể chỉnh sửa");
            case ONGOING -> {
                // Phien da mo: sinh vien co the dang thi, chi cho sua ten, mo ta, KEO DAI gio dong
                // va TANG so luot thi toi da
                if (!startAt.equals(exam.getStartAt()) || request.getDurationMinutes() != exam.getDurationMinutes()) {
                    throw new BusinessException(ErrorCode.EXAM_SESSION_LOCKED_FIELDS);
                }
                if (endAt.isBefore(exam.getEndAt())) {
                    throw new BusinessException(ErrorCode.EXAM_END_CANNOT_SHORTEN);
                }
                if (Duration.between(startAt, endAt).compareTo(MAX_WINDOW) > 0) {
                    throw new BusinessException(ErrorCode.EXAM_WINDOW_TOO_LONG);
                }
                // Giam so luot khi dang thi se lam sinh vien da thi nhieu hon bi vuot gioi han
                if (maxAttempts < exam.getMaxAttempts()) {
                    throw new BusinessException(ErrorCode.EXAM_MAX_ATTEMPTS_CANNOT_REDUCE);
                }
                // Phien da mo: cau hoi co the da duoc gan cho sinh vien dang thi
                if (questionsChanged) {
                    throw new BusinessException(ErrorCode.EXAM_SESSION_LOCKED_FIELDS,
                            "Phiên thi đã mở nên không thể đổi môn học, chủ đề và số câu hỏi");
                }
            }
            case UPCOMING -> {
                validateNewSchedule(startAt, endAt, request.getDurationMinutes(), now);
                if (courseId != null) {
                    selectedQuestions = validateCourseAndQuestions(courseId, request, actor);
                } else if (questionsChanged) {
                    validateTopics(topicIds, questionCount, actor);
                }
                if (questionsChanged) {
                    examQuestionRepository.deleteByExamId(exam.getId());
                    if (selectedQuestions != null && !selectedQuestions.isEmpty()) {
                        saveExamQuestions(exam, selectedQuestions);
                        questionCount = selectedQuestions.size();
                    }
                }
            }
        }

        exam.setTitle(request.getTitle().trim());
        exam.setDescription(normalizeDescription(request.getDescription()));
        exam.setStartAt(startAt);
        exam.setEndAt(endAt);
        exam.setDurationMinutes(request.getDurationMinutes());
        exam.setMaxAttempts(maxAttempts);
        exam.setCourseId(courseId);
        if (request.getAllowMobileExam() != null) {
            exam.setAllowMobileExam(request.getAllowMobileExam());
        }
        if (questionsChanged) {
            exam.setQuestionCount(questionCount);
            exam.getTopicIds().clear();
            exam.getTopicIds().addAll(topicIds);
        }
        return toResponse(examRepository.save(exam), now);
    }

    @Override
    @Transactional
    public DeleteExamSessionResponse deleteSession(String sessionId, UserPrincipal actor) {
        LocalDateTime now = now();
        VivaExam exam = findAccessible(sessionId, actor);

        // Chua co sinh vien nao vao: xoa han
        if (attemptRepository.countByExam_Id(exam.getId()) == 0) {
            examRepository.delete(exam);
            return new DeleteExamSessionResponse(DeleteExamSessionResponse.DELETED);
        }

        // Da co luot thi: giu du lieu, chi huy phien
        switch (examMapper.statusOf(exam, now)) {
            case ENDED -> throw new BusinessException(ErrorCode.EXAM_SESSION_HAS_ATTEMPTS);
            case CANCELLED -> { /* da huy tu truoc, khong lam gi them */ }
            default -> {
                exam.setCancelledAt(now);
                examRepository.save(exam);
            }
        }
        return new DeleteExamSessionResponse(DeleteExamSessionResponse.CANCELLED);
    }

    @Override
    @Transactional
    public ExamSessionResponse regeneratePasscode(String sessionId, UserPrincipal actor) {
        LocalDateTime now = now();
        VivaExam exam = findAccessible(sessionId, actor);
        switch (examMapper.statusOf(exam, now)) {
            case CANCELLED -> throw new BusinessException(ErrorCode.EXAM_SESSION_CANCELLED);
            case ENDED -> throw new BusinessException(ErrorCode.EXAM_SESSION_ENDED);
            default -> { /* UPCOMING, ONGOING: duoc tao lai */ }
        }

        // Ma moi phai khac ma cu, de ma da lo chac chan het hieu luc
        String passcode = codeGenerator.generatePasscode();
        while (passcode.equals(exam.getPasscode())) {
            passcode = codeGenerator.generatePasscode();
        }
        exam.setPasscode(passcode);
        return toResponse(examRepository.save(exam), now);
    }

    @Override
    @Transactional(readOnly = true)
    public LiveExamMonitorResponse getLiveExamMonitor(String examId, UserPrincipal principal) {
        VivaExam exam = findAccessible(examId, principal);
        LocalDateTime now = now();

        List<VivaAttempt> attempts = attemptRepository.findByExam_IdOrderByStartedAtDesc(exam.getId());

        // Group theo studentId, lay attempt moi nhat cua moi thi sinh
        Map<String, VivaAttempt> latestAttemptByStudent = new LinkedHashMap<>();
        for (VivaAttempt attempt : attempts) {
            latestAttemptByStudent.putIfAbsent(attempt.getStudentId(), attempt);
        }

        Set<String> studentIds = latestAttemptByStudent.keySet();
        Map<String, UserResponse> users = userService.getUsersByIds(studentIds);

        int totalQuestions = exam.getQuestionCount() != null && exam.getQuestionCount() > 0
                ? exam.getQuestionCount()
                : (int) examQuestionRepository.countByExam_Id(exam.getId());

        List<InterviewExchange> allExchanges = exchangeRepository.findByExamIdInOrder(exam.getId());
        Map<String, List<InterviewExchange>> exchangesByAttempt = allExchanges.stream()
                .collect(Collectors.groupingBy(e -> e.getAttempt().getId()));

        List<LiveExamineeResponse> examinees = new ArrayList<>();
        int totalCompleted = 0;
        int totalInProgress = 0;

        for (VivaAttempt attempt : latestAttemptByStudent.values()) {
            UserResponse user = users.get(attempt.getStudentId());
            String fullName = user != null ? user.getFullName() : attempt.getStudentId();
            String email = user != null ? user.getEmail() : null;

            long remainingSeconds = attempt.getStatus() == AttemptStatus.COMPLETED
                    ? 0
                    : Math.max(0, Duration.between(now, attempt.getDeadlineAt()).getSeconds());
            AttemptStatus status = (remainingSeconds == 0) ? AttemptStatus.COMPLETED : attempt.getStatus();

            if (status == AttemptStatus.COMPLETED) {
                totalCompleted++;
            } else {
                totalInProgress++;
            }

            List<InterviewExchange> attemptExchanges = exchangesByAttempt.getOrDefault(attempt.getId(), List.of());
            InterviewExchange latestExchange = attemptExchanges.isEmpty() ? null : attemptExchanges.get(attemptExchanges.size() - 1);

            Integer currentQuestionIndex;
            if (latestExchange != null && latestExchange.getExamQuestion() != null) {
                currentQuestionIndex = latestExchange.getExamQuestion().getOrderNo();
            } else if (status == AttemptStatus.COMPLETED) {
                currentQuestionIndex = totalQuestions;
            } else if (totalQuestions > 0) {
                currentQuestionIndex = 1;
            } else {
                currentQuestionIndex = 0;
            }

            int currentExchangeCount = attemptExchanges.size();

            boolean isSpeaking = status == AttemptStatus.IN_PROGRESS
                    && latestExchange != null
                    && latestExchange.getTranscript() == null
                    && latestExchange.getAiDecision() == null
                    && latestExchange.getAnswerEndedAt() == null;

            Instant lastActiveAt;
            if (latestExchange != null) {
                LocalDateTime dt = latestExchange.getAnswerEndedAt() != null
                        ? latestExchange.getAnswerEndedAt()
                        : (latestExchange.getUpdatedAt() != null ? latestExchange.getUpdatedAt() : latestExchange.getCreatedAt());
                lastActiveAt = dt != null ? dt.toInstant(ZoneOffset.UTC) : attempt.getStartedAt().toInstant(ZoneOffset.UTC);
            } else if (attempt.getCompletedAt() != null) {
                lastActiveAt = attempt.getCompletedAt().toInstant(ZoneOffset.UTC);
            } else {
                lastActiveAt = attempt.getStartedAt().toInstant(ZoneOffset.UTC);
            }

            examinees.add(LiveExamineeResponse.builder()
                    .studentId(attempt.getStudentId())
                    .fullName(fullName)
                    .email(email)
                    .attemptId(attempt.getId())
                    .status(status)
                    .currentQuestionIndex(currentQuestionIndex)
                    .totalQuestions(totalQuestions)
                    .currentExchangeCount(currentExchangeCount)
                    .remainingSeconds(remainingSeconds)
                    .isSpeaking(isSpeaking)
                    .lastActiveAt(lastActiveAt)
                    .build());
        }

        examinees.sort(Comparator
                .comparing((LiveExamineeResponse e) -> e.getStatus() == AttemptStatus.IN_PROGRESS ? 0 : 1)
                .thenComparing(LiveExamineeResponse::getLastActiveAt, Comparator.nullsLast(Comparator.reverseOrder())));

        return LiveExamMonitorResponse.builder()
                .examId(exam.getId())
                .examTitle(exam.getTitle())
                .totalStudentsJoined(latestAttemptByStudent.size())
                .totalCompleted(totalCompleted)
                .totalInProgress(totalInProgress)
                .examinees(examinees)
                .build();
    }

    /* ===================== Validate ===================== */

    /** Dung khi tao moi, hoac khi sua phien chua mo. */
    private void validateNewSchedule(LocalDateTime startAt, LocalDateTime endAt, int durationMinutes, LocalDateTime now) {
        if (!endAt.isAfter(startAt)) {
            throw new BusinessException(ErrorCode.EXAM_TIME_RANGE_INVALID);
        }
        if (startAt.isBefore(now.minus(START_PAST_TOLERANCE))) {
            throw new BusinessException(ErrorCode.EXAM_START_IN_PAST);
        }
        if (startAt.isAfter(now.plus(MAX_START_AHEAD))) {
            throw new BusinessException(ErrorCode.EXAM_START_TOO_FAR);
        }
        Duration window = Duration.between(startAt, endAt);
        if (window.compareTo(MAX_WINDOW) > 0) {
            throw new BusinessException(ErrorCode.EXAM_WINDOW_TOO_LONG);
        }
        if (Duration.ofMinutes(durationMinutes).compareTo(window) > 0) {
            throw new BusinessException(ErrorCode.EXAM_DURATION_EXCEEDS_WINDOW);
        }
    }

    /** Bo id trung va khoang trang thua. null / rong = khong chon chu de. */
    private Set<String> normalizeTopicIds(List<String> topicIds) {
        Set<String> result = new LinkedHashSet<>();
        if (topicIds != null) {
            for (String topicId : topicIds) {
                if (StringUtils.hasText(topicId)) {
                    result.add(topicId.trim());
                }
            }
        }
        return result;
    }

    /**
     * Chu de phai ton tai, thuoc quyen cua nguoi thao tac (admin: tat ca) va co du cau hoi DA DUYET.
     * Khong chon chu de nao thi khong kiem tra (phien lay cau hoi theo cach mac dinh).
     */
    private void validateTopics(Set<String> topicIds, Integer questionCount, UserPrincipal actor) {
        if (topicIds.isEmpty()) {
            return;
        }
        List<TopicResponse> topics = contentService.getTopicsByIds(actor, topicIds);
        if (topics.size() != topicIds.size()) {
            throw new BusinessException(ErrorCode.EXAM_TOPIC_INVALID);
        }
        int approved = topics.stream().mapToInt(TopicResponse::getApprovedQuestionCount).sum();
        int needed = questionCount == null ? 1 : questionCount;
        if (approved < needed) {
            throw new BusinessException(ErrorCode.EXAM_NOT_ENOUGH_QUESTIONS,
                    "Các chủ đề đã chọn chỉ có " + approved + " câu hỏi đã duyệt, chưa đủ " + needed
                            + " câu cho phiên thi. Hãy duyệt thêm câu hỏi, chọn thêm chủ đề hoặc giảm số câu hỏi");
        }
    }

    private ExamSessionStatus parseStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        try {
            return ExamSessionStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "Trạng thái lọc không hợp lệ. Giá trị cho phép: UPCOMING, ONGOING, ENDED, CANCELLED");
        }
    }

    /* ===================== Ho tro ===================== */

    private VivaExam findAccessible(String sessionId, UserPrincipal actor) {
        VivaExam exam = examRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EXAM_SESSION_NOT_FOUND));
        if (actor.getRole() != RoleName.ADMIN && !exam.getLecturerId().equals(actor.getId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Bạn chỉ được thao tác trên phiên thi do mình tạo");
        }
        return exam;
    }

    /** Dieu kien loc theo trang thai, khop voi ExamMapper.statusOf. */
    private Specification<VivaExam> statusSpec(ExamSessionStatus status, LocalDateTime now) {
        return (root, query, cb) -> switch (status) {
            case CANCELLED -> cb.isNotNull(root.get("cancelledAt"));
            case UPCOMING -> cb.and(
                    cb.isNull(root.get("cancelledAt")),
                    cb.greaterThan(root.<LocalDateTime>get("startAt"), now));
            case ONGOING -> cb.and(
                    cb.isNull(root.get("cancelledAt")),
                    cb.lessThanOrEqualTo(root.<LocalDateTime>get("startAt"), now),
                    cb.greaterThan(root.<LocalDateTime>get("endAt"), now));
            case ENDED -> cb.and(
                    cb.isNull(root.get("cancelledAt")),
                    cb.lessThanOrEqualTo(root.<LocalDateTime>get("endAt"), now));
        };
    }

    private Map<String, Long> countAttempts(List<VivaExam> exams) {
        Map<String, Long> counts = new HashMap<>();
        if (exams.isEmpty()) {
            return counts;
        }
        List<String> examIds = exams.stream().map(VivaExam::getId).toList();
        for (Object[] row : attemptRepository.countGroupedByExamIds(examIds)) {
            counts.put((String) row[0], (Long) row[1]);
        }
        return counts;
    }

    private ExamSessionResponse toResponse(VivaExam exam, LocalDateTime now) {
        String ownerName = userService.getFullNamesByIds(List.of(exam.getLecturerId())).get(exam.getLecturerId());
        long attemptCount = attemptRepository.countByExam_Id(exam.getId());
        Map<String, String> topicNames = contentService.getTopicNames(exam.getTopicIds());
        String courseName = null;
        if (StringUtils.hasText(exam.getCourseId())) {
            courseName = courseRepository.findById(exam.getCourseId()).map(Course::getName).orElse(null);
        }
        List<VivaExamQuestion> examQuestions = examQuestionRepository.findWithQuestionByExamId(exam.getId());
        return examMapper.toResponse(exam, ownerName, attemptCount, topicNames, courseName, examQuestions, now);
    }

    private List<Question> validateCourseAndQuestions(String courseId, ExamSessionRequest request, UserPrincipal actor) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND, "Không tìm thấy môn học: " + courseId));

        if (!courseRubricService.isRubricReadyForGrading(course.getId())) {
            throw new BusinessException(ErrorCode.COURSE_RUBRIC_REQUIRED,
                    "Môn học bắt buộc phải có Rubric hoàn chỉnh (tổng trọng số 100%) trước khi mở ca thi");
        }

        if (request.getQuestionIds() != null && !request.getQuestionIds().isEmpty()) {
            List<String> qIds = request.getQuestionIds().stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .distinct()
                    .toList();
            List<Question> questions = questionRepository.findAllById(qIds);
            if (questions.size() != qIds.size()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Một hoặc nhiều câu hỏi không tồn tại trong hệ thống");
            }
            for (Question q : questions) {
                if (!QUESTION_APPROVED.equalsIgnoreCase(q.getStatus())) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "Câu hỏi " + q.getId() + " chưa được phê duyệt (APPROVED)");
                }
                if (q.getChapter() != null && q.getChapter().getCourse() != null
                        && !courseId.equals(q.getChapter().getCourse().getId())) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "Câu hỏi " + q.getId() + " không thuộc môn học đã chọn");
                }
            }
            Map<String, Question> questionMap = questions.stream().collect(Collectors.toMap(Question::getId, q -> q));
            return qIds.stream().map(questionMap::get).toList();
        }

        if (request.getChapterIds() != null && !request.getChapterIds().isEmpty()) {
            List<String> chapIds = request.getChapterIds().stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .distinct()
                    .toList();
            List<Chapter> chapters = chapterRepository.findAllById(chapIds);
            if (chapters.size() != chapIds.size()) {
                throw new BusinessException(ErrorCode.CHAPTER_NOT_FOUND, "Một hoặc nhiều chương không tồn tại");
            }
            for (Chapter c : chapters) {
                if (!courseId.equals(c.getCourse().getId())) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "Chương " + c.getId() + " không thuộc môn học đã chọn");
                }
            }
            int needed = request.getQuestionCount() == null ? 1 : request.getQuestionCount();
            long approvedCount = questionRepository.countByChapter_IdInAndStatus(chapIds, QUESTION_APPROVED);
            if (approvedCount < needed) {
                throw new BusinessException(ErrorCode.EXAM_NOT_ENOUGH_QUESTIONS,
                        "Các chương đã chọn chỉ có " + approvedCount + " câu hỏi đã duyệt, chưa đủ " + needed
                                + " câu cho phiên thi. Hãy duyệt thêm câu hỏi hoặc giảm số câu hỏi");
            }
            return pickRandomFromChapters(chapIds, needed);
        }

        int needed = request.getQuestionCount() == null ? 1 : request.getQuestionCount();
        long approvedCount = questionRepository.countByChapter_Course_IdAndStatus(courseId, QUESTION_APPROVED);
        if (approvedCount < needed) {
            throw new BusinessException(ErrorCode.EXAM_NOT_ENOUGH_QUESTIONS,
                    "Môn học chỉ có " + approvedCount + " câu hỏi đã duyệt, chưa đủ " + needed
                            + " câu cho phiên thi. Hãy duyệt thêm câu hỏi hoặc giảm số câu hỏi");
        }
        return null;
    }

    private List<Question> pickRandomFromChapters(Collection<String> chapterIds, int count) {
        List<Question> questions = questionRepository.findByChapter_IdInAndStatus(chapterIds, QUESTION_APPROVED);
        Map<String, List<Question>> byChapter = new LinkedHashMap<>();
        for (Question question : questions) {
            byChapter.computeIfAbsent(question.getChapter().getId(), key -> new ArrayList<>()).add(question);
        }
        List<List<Question>> groups = new ArrayList<>(byChapter.values());
        Collections.shuffle(groups);
        groups.forEach(Collections::shuffle);

        List<Question> picked = new ArrayList<>();
        boolean added = true;
        for (int round = 0; added && picked.size() < count; round++) {
            added = false;
            for (List<Question> group : groups) {
                if (round < group.size() && picked.size() < count) {
                    picked.add(group.get(round));
                    added = true;
                }
            }
        }
        return picked;
    }

    private void saveExamQuestions(VivaExam exam, List<Question> questions) {
        int orderNo = 1;
        for (Question question : questions) {
            VivaExamQuestion eq = VivaExamQuestion.builder()
                    .exam(exam)
                    .question(question)
                    .orderNo(orderNo++)
                    .weight(BigDecimal.ONE)
                    .build();
            eq.setId(idGenerator.next(EXAM_QUESTION_ID_PREFIX));
            examQuestionRepository.save(eq);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }

    /** Doi ve gio UTC, bo phan le cua giay de so sanh va luu DB on dinh. */
    private LocalDateTime toUtc(OffsetDateTime value) {
        return value.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime().truncatedTo(ChronoUnit.SECONDS);
    }

    private String normalizeDescription(String description) {
        return StringUtils.hasText(description) ? description.trim() : null;
    }

    private String escapeLike(String value) {
        return value
                .replace(String.valueOf(LIKE_ESCAPE), "" + LIKE_ESCAPE + LIKE_ESCAPE)
                .replace("%", LIKE_ESCAPE + "%")
                .replace("_", LIKE_ESCAPE + "_");
    }
}
