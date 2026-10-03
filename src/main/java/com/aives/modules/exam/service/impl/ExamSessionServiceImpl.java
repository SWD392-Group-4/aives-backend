package com.aives.modules.exam.service.impl;

import com.aives.common.dto.PageResponse;
import com.aives.common.exception.BusinessException;
import com.aives.common.exception.ErrorCode;
import com.aives.common.security.UserPrincipal;
import com.aives.modules.auth.enums.Role;
import com.aives.modules.auth.service.UserService;
import com.aives.modules.exam.dto.DeleteExamSessionResponse;
import com.aives.modules.exam.dto.ExamSessionRequest;
import com.aives.modules.exam.dto.ExamSessionResponse;
import com.aives.modules.exam.entity.VivaExam;
import com.aives.modules.exam.enums.ExamSessionStatus;
import com.aives.modules.exam.mapper.ExamMapper;
import com.aives.modules.exam.repository.VivaAttemptRepository;
import com.aives.modules.exam.repository.VivaExamRepository;
import com.aives.modules.exam.service.ExamSessionService;
import com.aives.modules.exam.util.ExamCodeGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private final ExamCodeGenerator codeGenerator;
    private final ExamMapper examMapper;
    private final UserService userService;
    private final Clock clock;

    @Override
    @Transactional
    public ExamSessionResponse createSession(ExamSessionRequest request, UserPrincipal actor) {
        LocalDateTime now = now();
        LocalDateTime startAt = toUtc(request.getStartAt());
        LocalDateTime endAt = toUtc(request.getEndAt());
        validateNewSchedule(startAt, endAt, request.getDurationMinutes(), now);

        VivaExam exam = VivaExam.builder()
                .lecturerId(actor.getId())
                .title(request.getTitle().trim())
                .description(normalizeDescription(request.getDescription()))
                .passcode(codeGenerator.generatePasscode())
                .durationMinutes(request.getDurationMinutes())
                .startAt(startAt)
                .endAt(endAt)
                .build();
        exam.setId(codeGenerator.generateExamId());

        return toResponse(examRepository.save(exam), now);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ExamSessionResponse> getSessions(
            UserPrincipal actor, String status, String keyword, int page, int size) {
        LocalDateTime now = now();
        ExamSessionStatus statusFilter = parseStatus(status);

        // LECTURER chi thay phien cua minh, ADMIN thay tat ca
        Specification<VivaExam> spec = (root, query, cb) -> cb.conjunction();
        if (actor.getRole() != Role.ADMIN) {
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

        List<ExamSessionResponse> items = exams.stream()
                .map(exam -> examMapper.toResponse(
                        exam,
                        ownerNames.get(exam.getLecturerId()),
                        attemptCounts.getOrDefault(exam.getId(), 0L),
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

        switch (examMapper.statusOf(exam, now)) {
            case CANCELLED -> throw new BusinessException(ErrorCode.EXAM_SESSION_CANCELLED,
                    "Phiên thi đã bị huỷ nên không thể chỉnh sửa");
            case ENDED -> throw new BusinessException(ErrorCode.EXAM_SESSION_ENDED,
                    "Phiên thi đã kết thúc nên không thể chỉnh sửa");
            case ONGOING -> {
                // Phien da mo: sinh vien co the dang thi, chi cho sua ten, mo ta va KEO DAI gio dong
                if (!startAt.equals(exam.getStartAt()) || request.getDurationMinutes() != exam.getDurationMinutes()) {
                    throw new BusinessException(ErrorCode.EXAM_SESSION_LOCKED_FIELDS);
                }
                if (endAt.isBefore(exam.getEndAt())) {
                    throw new BusinessException(ErrorCode.EXAM_END_CANNOT_SHORTEN);
                }
                if (Duration.between(startAt, endAt).compareTo(MAX_WINDOW) > 0) {
                    throw new BusinessException(ErrorCode.EXAM_WINDOW_TOO_LONG);
                }
            }
            case UPCOMING -> validateNewSchedule(startAt, endAt, request.getDurationMinutes(), now);
        }

        exam.setTitle(request.getTitle().trim());
        exam.setDescription(normalizeDescription(request.getDescription()));
        exam.setStartAt(startAt);
        exam.setEndAt(endAt);
        exam.setDurationMinutes(request.getDurationMinutes());
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
        if (actor.getRole() != Role.ADMIN && !exam.getLecturerId().equals(actor.getId())) {
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
        return examMapper.toResponse(exam, ownerName, attemptCount, now);
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
