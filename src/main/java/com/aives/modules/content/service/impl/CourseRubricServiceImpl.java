package com.aives.modules.content.service.impl;

import com.aives.common.exception.BusinessException;
import com.aives.common.exception.ErrorCode;
import com.aives.common.security.UserPrincipal;
import com.aives.common.util.AuditTime;
import com.aives.common.util.RandomIdGenerator;
import com.aives.modules.auth.entity.User;
import com.aives.modules.auth.repository.UserRepository;
import com.aives.modules.content.dto.CourseRubricResponse;
import com.aives.modules.content.dto.RubricCriteriaDto;
import com.aives.modules.content.dto.UpdateCourseRubricRequest;
import com.aives.modules.content.entity.Course;
import com.aives.modules.content.entity.Rubric;
import com.aives.modules.content.entity.RubricCriterion;
import com.aives.modules.content.enums.AuditAction;
import com.aives.modules.content.repository.CourseRepository;
import com.aives.modules.content.repository.RubricCriterionRepository;
import com.aives.modules.content.repository.RubricRepository;
import com.aives.modules.content.service.AcademicAuditLogService;
import com.aives.modules.content.service.CourseRubricService;
import com.aives.modules.exam.repository.VivaExamRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
public class CourseRubricServiceImpl implements CourseRubricService {

    private static final BigDecimal TARGET_WEIGHT = new BigDecimal("100.00");
    private static final BigDecimal WEIGHT_TOLERANCE = new BigDecimal("0.05");

    private final CourseRepository courseRepository;
    private final RubricRepository rubricRepository;
    private final RubricCriterionRepository rubricCriterionRepository;
    private final VivaExamRepository vivaExamRepository;
    private final UserRepository userRepository;
    private final AcademicAuditLogService academicAuditLogService;
    private final RandomIdGenerator idGenerator;

    @Override
    @Transactional(readOnly = true)
    public CourseRubricResponse getRubricByCourseId(String courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND, "Không tìm thấy môn học: " + courseId));

        Rubric rubric = rubricRepository.findByCourse_Id(course.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RUBRIC_NOT_FOUND, "Môn học chưa có Rubric đánh giá"));

        boolean isLocked = isCourseRubricLocked(course.getId(), rubric);
        List<RubricCriterion> criteria = rubricCriterionRepository.findByRubric_IdOrderByOrderNoAsc(rubric.getId());

        return toCourseRubricResponse(rubric, course.getId(), isLocked, criteria);
    }

    @Override
    @Transactional
    public CourseRubricResponse updateRubric(String courseId, UpdateCourseRubricRequest request, UserPrincipal principal) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND, "Không tìm thấy môn học: " + courseId));

        Rubric rubric = rubricRepository.findByCourse_Id(course.getId()).orElse(null);

        if (rubric != null && isCourseRubricLocked(course.getId(), rubric)) {
            throw new BusinessException(ErrorCode.RUBRIC_LOCKED,
                    "Rubric của môn học đã bị khóa do đã có ca thi đang mở hoặc đã kết thúc (BR-BANK-006)");
        }

        validateCriteriaWeight(request.getCriteria());

        boolean isNew = (rubric == null);
        if (isNew) {
            rubric = Rubric.builder()
                    .course(course)
                    .createdBy(principal.getId())
                    .name(request.getName().trim())
                    .description(request.getDescription())
                    .maxScore(new BigDecimal("10.00"))
                    .isLocked(false)
                    .build();
            rubric.setId(idGenerator.next("RB"));
        } else {
            rubric.setName(request.getName().trim());
            rubric.setDescription(request.getDescription());
        }
        // Sua rubric thi phai duyet lai
        rubric.setApprovedAt(null);
        rubric.setApprovedBy(null);

        Rubric savedRubric = rubricRepository.save(rubric);

        if (!isNew) {
            rubricCriterionRepository.deleteByRubric_Id(savedRubric.getId());
            rubricCriterionRepository.flush();
        }

        List<RubricCriterion> criteriaToSave = new ArrayList<>();
        int order = 1;
        for (RubricCriteriaDto dto : request.getCriteria()) {
            String critId = (dto.getId() != null && !dto.getId().isBlank()) ? dto.getId() : idGenerator.next("RC");
            RubricCriterion criterion = RubricCriterion.builder()
                    .rubric(savedRubric)
                    .name(dto.getName().trim())
                    .description(dto.getDescription().trim())
                    .weightPercent(dto.getWeightPercent())
                    .orderNo(dto.getOrderNo() != null ? dto.getOrderNo() : order++)
                    .build();
            criterion.setId(critId);
            criteriaToSave.add(criterion);
        }

        List<RubricCriterion> savedCriteria = rubricCriterionRepository.saveAll(criteriaToSave);

        String actorName = principal.getEmail();
        if (principal.getId() != null) {
            actorName = userRepository.findById(principal.getId())
                    .map(User::getFullName)
                    .orElse(principal.getEmail());
        }

        academicAuditLogService.logActivity(
                course.getId(),
                null,
                principal.getId(),
                actorName,
                AuditAction.UPDATE_RUBRIC,
                "RUBRIC",
                savedRubric.getId(),
                Map.of(
                        "rubricName", savedRubric.getName(),
                        "criteriaCount", savedCriteria.size(),
                        "isNew", isNew
                )
        );

        return toCourseRubricResponse(savedRubric, course.getId(), false, savedCriteria);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isRubricReadyForGrading(String courseId) {
        Rubric rubric = rubricRepository.findByCourse_Id(courseId).orElse(null);
        if (rubric == null || rubric.getApprovedAt() == null) {
            return false;
        }
        List<RubricCriterion> criteria = rubricCriterionRepository.findByRubric_IdOrderByOrderNoAsc(rubric.getId());
        if (criteria.isEmpty()) {
            return false;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (RubricCriterion c : criteria) {
            if (c.getWeightPercent() != null) {
                sum = sum.add(c.getWeightPercent());
            }
        }
        return sum.subtract(TARGET_WEIGHT).abs().compareTo(WEIGHT_TOLERANCE) <= 0;
    }

    @Override
    @Transactional
    public CourseRubricResponse approveRubric(String courseId, UserPrincipal principal) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND, "Không tìm thấy môn học: " + courseId));
        Rubric rubric = rubricRepository.findByCourse_Id(course.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RUBRIC_NOT_FOUND, "Môn học chưa có Rubric, hãy tạo và lưu Rubric trước"));

        List<RubricCriterion> criteria = rubricCriterionRepository.findByRubric_IdOrderByOrderNoAsc(rubric.getId());
        BigDecimal sum = BigDecimal.ZERO;
        for (RubricCriterion c : criteria) {
            if (c.getWeightPercent() != null) {
                sum = sum.add(c.getWeightPercent());
            }
        }
        if (criteria.isEmpty() || sum.subtract(TARGET_WEIGHT).abs().compareTo(WEIGHT_TOLERANCE) > 0) {
            throw new BusinessException(ErrorCode.RUBRIC_WEIGHT_INVALID,
                    "Chỉ duyệt được Rubric có tổng trọng số đúng 100% (hiện tại " + sum + "%)");
        }

        if (rubric.getApprovedAt() == null) {
            rubric.setApprovedAt(LocalDateTime.now());
            rubric.setApprovedBy(principal != null ? principal.getId() : null);
            rubric = rubricRepository.save(rubric);
            log.info("Approved rubric: id={}, courseId={}", rubric.getId(), courseId);
        }
        return toCourseRubricResponse(rubric, course.getId(), isCourseRubricLocked(course.getId(), rubric), criteria);
    }

    private boolean isCourseRubricLocked(String courseId, Rubric rubric) {
        if (Boolean.TRUE.equals(rubric.getIsLocked())) {
            return true;
        }
        return vivaExamRepository.hasStartedExamsForCourse(courseId, LocalDateTime.now());
    }

    private void validateCriteriaWeight(List<RubricCriteriaDto> criteria) {
        if (criteria == null || criteria.isEmpty()) {
            throw new BusinessException(ErrorCode.RUBRIC_WEIGHT_INVALID, "Danh sách tiêu chí không được để trống");
        }
        BigDecimal total = BigDecimal.ZERO;
        for (RubricCriteriaDto item : criteria) {
            if (item.getWeightPercent() == null || item.getWeightPercent().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(ErrorCode.RUBRIC_WEIGHT_INVALID,
                        "Trọng số của từng tiêu chí phải lớn hơn 0%");
            }
            total = total.add(item.getWeightPercent());
        }

        if (total.subtract(TARGET_WEIGHT).abs().compareTo(WEIGHT_TOLERANCE) > 0) {
            throw new BusinessException(ErrorCode.RUBRIC_WEIGHT_INVALID,
                    "Tổng trọng số các tiêu chí trong Rubric phải bằng đúng 100% (Hiện tại là " + total + "%)");
        }
    }

    private CourseRubricResponse toCourseRubricResponse(
            Rubric rubric, String courseId, boolean isLocked, List<RubricCriterion> criteria) {
        List<RubricCriteriaDto> criteriaDtos = criteria.stream()
                .map(c -> RubricCriteriaDto.builder()
                        .id(c.getId())
                        .name(c.getName())
                        .description(c.getDescription())
                        .weightPercent(c.getWeightPercent())
                        .orderNo(c.getOrderNo())
                        .build())
                .toList();

        return CourseRubricResponse.builder()
                .id(rubric.getId())
                .courseId(courseId)
                .name(rubric.getName())
                .description(rubric.getDescription())
                .maxScore(rubric.getMaxScore())
                .isLocked(isLocked)
                .criteria(criteriaDtos)
                .approved(rubric.getApprovedAt() != null)
                .approvedAt(AuditTime.toInstant(rubric.getApprovedAt()))
                .createdAt(AuditTime.toInstant(rubric.getCreatedAt()))
                .updatedAt(AuditTime.toInstant(rubric.getUpdatedAt()))
                .build();
    }
}
