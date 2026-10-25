package com.aives.modules.content.service.impl;

import com.aives.common.exception.BusinessException;
import com.aives.common.exception.ErrorCode;
import com.aives.common.util.RandomIdGenerator;
import com.aives.modules.auth.entity.User;
import com.aives.modules.auth.repository.UserRepository;
import com.aives.modules.content.document.AcademicAuditLog;
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
import com.aives.modules.content.service.IAcademicAuditLogService;
import com.aives.modules.content.service.ICourseRubricService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseRubricServiceImpl implements ICourseRubricService {

    private final CourseRepository courseRepository;
    private final RubricRepository rubricRepository;
    private final RubricCriterionRepository criterionRepository;
    private final UserRepository userRepository;
    private final IAcademicAuditLogService auditLogService;
    private final RandomIdGenerator idGenerator;

    @Override
    @Transactional(readOnly = true)
    public CourseRubricResponse getRubricByCourseId(String courseId) {
        log.info("Lay thong tin Rubric cho mon hoc: courseId={}", courseId);
        if (!courseRepository.existsById(courseId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy môn học " + courseId);
        }

        Rubric rubric = rubricRepository.findByCourse_Id(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUBRIC_NOT_FOUND, "Môn học chưa được thiết lập Rubric"));

        List<RubricCriterion> criteriaList = criterionRepository.findByRubric_IdOrderByOrderNoAsc(rubric.getId());
        return mapToResponse(rubric, criteriaList);
    }

    @Override
    @Transactional
    public CourseRubricResponse updateRubric(String courseId, UpdateCourseRubricRequest request, String lecturerId) {
        log.info("Cap nhat Rubric cho mon hoc: courseId={}, lecturerId={}", courseId, lecturerId);
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy môn học " + courseId));

        // 1. Validation BR-BANK-001: Tong trong so cac tieu chi bat buoc phai bang dung 100.00%
        BigDecimal totalWeight = BigDecimal.ZERO;
        for (RubricCriteriaDto dto : request.getCriteria()) {
            if (dto.getWeightPercent() == null) {
                throw new BusinessException(ErrorCode.RUBRIC_WEIGHT_INVALID, "Trọng số tiêu chí không được để trống");
            }
            totalWeight = totalWeight.add(dto.getWeightPercent());
        }

        if (totalWeight.compareTo(new BigDecimal("100.00")) != 0 && totalWeight.compareTo(new BigDecimal("100")) != 0) {
            log.warn("Tong trong so rubric khong dat 100%: totalWeight={}, courseId={}", totalWeight, courseId);
            throw new BusinessException(ErrorCode.RUBRIC_WEIGHT_INVALID,
                    "Tổng trọng số các tiêu chí trong Rubric phải bằng đúng 100% (Hiện tại: " + totalWeight + "%)");
        }

        // 2. Kiem tra cờ is_locked (BR-BANK-006): Neu rubric da bi khoa thi chan cap nhat (403 Forbidden)
        Rubric rubric = rubricRepository.findByCourse_Id(courseId).orElse(null);
        Map<String, Object> beforeState = new HashMap<>();

        if (rubric != null) {
            if (Boolean.TRUE.equals(rubric.getIsLocked())) {
                log.warn("Tu choi sua Rubric do da bi khoa (BR-BANK-006): rubricId={}, courseId={}", rubric.getId(), courseId);
                throw new BusinessException(ErrorCode.ACCESS_DENIED,
                        "Rubric đã bị khóa do môn học đã có ca thi đang mở hoặc kết thúc (BR-BANK-006)");
            }
            beforeState.put("name", rubric.getName());
            beforeState.put("description", rubric.getDescription());
            beforeState.put("criteriaCount", rubric.getCriteria() != null ? rubric.getCriteria().size() : 0);

            rubric.setName(request.getName());
            rubric.setDescription(request.getDescription());
        } else {
            rubric = Rubric.builder()
                    .course(course)
                    .createdBy(lecturerId)
                    .name(request.getName())
                    .description(request.getDescription())
                    .maxScore(new BigDecimal("10.00"))
                    .isLocked(false)
                    .build();
            rubric.setId(idGenerator.next("RU"));
        }

        rubric = rubricRepository.save(rubric);

        // 3. Cap nhat danh sach tieu chi
        criterionRepository.deleteByRubric_Id(rubric.getId());

        List<RubricCriterion> savedCriteria = new ArrayList<>();
        for (RubricCriteriaDto cDto : request.getCriteria()) {
            RubricCriterion criterion = RubricCriterion.builder()
                    .rubric(rubric)
                    .name(cDto.getName())
                    .description(cDto.getDescription())
                    .weightPercent(cDto.getWeightPercent())
                    .orderNo(cDto.getOrderNo())
                    .build();
            criterion.setId(idGenerator.next("RC"));
            savedCriteria.add(criterionRepository.save(criterion));
        }

        // 4. Ghi Audit Log vao MongoDB (BR-BANK-007)
        String actorName = userRepository.findById(lecturerId)
                .map(User::getFullName)
                .orElse(lecturerId);

        Map<String, Object> afterState = new HashMap<>();
        afterState.put("name", rubric.getName());
        afterState.put("criteriaCount", savedCriteria.size());
        afterState.put("totalWeight", totalWeight);

        Map<String, Object> details = new HashMap<>();
        details.put("before_state", beforeState);
        details.put("after_state", afterState);

        auditLogService.logActivity(AcademicAuditLog.builder()
                .courseId(courseId)
                .actorId(lecturerId)
                .actorName(actorName)
                .action(AuditAction.UPDATE_RUBRIC)
                .targetEntity("RUBRIC")
                .targetId(rubric.getId())
                .details(details)
                .build());

        return mapToResponse(rubric, savedCriteria);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isRubricReadyForGrading(String courseId) {
        Rubric rubric = rubricRepository.findByCourse_Id(courseId).orElse(null);
        if (rubric == null) {
            return false;
        }

        List<RubricCriterion> criteria = criterionRepository.findByRubric_IdOrderByOrderNoAsc(rubric.getId());
        if (criteria.isEmpty()) {
            return false;
        }

        BigDecimal total = criteria.stream()
                .map(RubricCriterion::getWeightPercent)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return total.compareTo(new BigDecimal("100.00")) == 0 || total.compareTo(new BigDecimal("100")) == 0;
    }

    private CourseRubricResponse mapToResponse(Rubric rubric, List<RubricCriterion> criteriaList) {
        List<RubricCriteriaDto> dtoList = criteriaList.stream().map(c -> RubricCriteriaDto.builder()
                .id(c.getId())
                .name(c.getName())
                .description(c.getDescription())
                .weightPercent(c.getWeightPercent())
                .orderNo(c.getOrderNo())
                .build()).toList();

        return CourseRubricResponse.builder()
                .id(rubric.getId())
                .courseId(rubric.getCourse().getId())
                .name(rubric.getName())
                .description(rubric.getDescription())
                .maxScore(rubric.getMaxScore())
                .isLocked(rubric.getIsLocked())
                .criteria(dtoList)
                .createdAt(rubric.getCreatedAt())
                .updatedAt(rubric.getUpdatedAt())
                .build();
    }
}