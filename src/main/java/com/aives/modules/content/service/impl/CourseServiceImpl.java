package com.aives.modules.content.service.impl;

import com.aives.common.exception.BusinessException;
import com.aives.common.exception.ErrorCode;
import com.aives.common.util.RandomIdGenerator;
import com.aives.modules.content.dto.CourseResponse;
import com.aives.modules.content.dto.CreateCourseRequest;
import com.aives.modules.content.dto.UpdateCourseRequest;
import com.aives.modules.content.entity.Chapter;
import com.aives.modules.content.entity.Course;
import com.aives.modules.content.repository.ChapterRepository;
import com.aives.modules.content.repository.CourseLecturerRepository;
import com.aives.modules.content.repository.CourseRepository;
import com.aives.modules.content.repository.MaterialRepository;
import com.aives.modules.content.repository.QuestionRepository;
import com.aives.modules.content.repository.RubricCriterionRepository;
import com.aives.modules.content.repository.RubricRepository;
import com.aives.modules.content.service.CourseService;
import com.aives.modules.exam.repository.VivaExamRepository;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseServiceImpl implements CourseService {

    private final CourseRepository courseRepository;
    private final ChapterRepository chapterRepository;
    private final CourseLecturerRepository courseLecturerRepository;
    private final RubricRepository rubricRepository;
    private final RubricCriterionRepository rubricCriterionRepository;
    private final MaterialRepository materialRepository;
    private final QuestionRepository questionRepository;
    private final VivaExamRepository vivaExamRepository;
    private final RandomIdGenerator idGenerator;

    @Override
    @Transactional(readOnly = true)
    public List<CourseResponse> getAllCourses() {
        return courseRepository.findAll().stream()
                .sorted(Comparator.comparing(Course::getCourseCode, String.CASE_INSENSITIVE_ORDER))
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CourseResponse getCourseById(String id) {
        return toResponse(findCourse(id));
    }

    @Override
    @Transactional
    public CourseResponse createCourse(CreateCourseRequest request) {
        String code = normalizeCode(request.getCourseCode());
        if (courseRepository.existsByCourseCodeIgnoreCase(code)) {
            throw new BusinessException(ErrorCode.DATA_CONFLICT, "Mã môn học " + code + " đã tồn tại");
        }

        Course course = Course.builder()
                .courseCode(code)
                .name(request.getName().trim())
                .description(trimToNull(request.getDescription()))
                .build();
        course.setId(idGenerator.next("CR"));

        Course saved = courseRepository.save(course);
        log.info("Created course: id={}, code={}", saved.getId(), saved.getCourseCode());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public CourseResponse updateCourse(String id, UpdateCourseRequest request) {
        Course course = findCourse(id);
        String code = normalizeCode(request.getCourseCode());
        if (courseRepository.existsByCourseCodeIgnoreCaseAndIdNot(code, id)) {
            throw new BusinessException(ErrorCode.DATA_CONFLICT, "Mã môn học " + code + " đã tồn tại");
        }

        course.setCourseCode(code);
        course.setName(request.getName().trim());
        course.setDescription(trimToNull(request.getDescription()));

        Course saved = courseRepository.save(course);
        log.info("Updated course: id={}, code={}", saved.getId(), saved.getCourseCode());
        return toResponse(saved);
    }

    /**
     * Chi xoa duoc mon chua co phien thi, chua co cau hoi va tai lieu.
     * Khi xoa: xoa luon rubric + tieu chi, danh sach giang vien phu trach va cac chuong (dang rong).
     */
    @Override
    @Transactional
    public void deleteCourse(String id) {
        Course course = findCourse(id);

        if (vivaExamRepository.existsByCourseId(id)) {
            throw new BusinessException(ErrorCode.DATA_CONFLICT,
                    "Môn học đã được dùng cho phiên thi nên không thể xoá");
        }

        List<Chapter> chapters = chapterRepository.findByCourse_IdOrderByOrderNoAsc(id);
        long questionCount = questionRepository.countByChapter_Course_Id(id);
        long materialCount = chapters.isEmpty()
                ? 0
                : materialRepository.countByChapterIdIn(chapters.stream().map(Chapter::getId).toList());
        if (questionCount > 0 || materialCount > 0) {
            throw new BusinessException(ErrorCode.DATA_CONFLICT,
                    "Môn học đang có " + materialCount + " tài liệu và " + questionCount
                            + " câu hỏi. Hãy xoá tài liệu và câu hỏi trước khi xoá môn học");
        }

        rubricRepository.findByCourse_Id(id).ifPresent(rubric -> {
            if (questionRepository.existsByRubric_Id(rubric.getId())) {
                throw new BusinessException(ErrorCode.DATA_CONFLICT,
                        "Rubric của môn học đang được câu hỏi khác sử dụng nên không thể xoá môn học");
            }
            rubricCriterionRepository.deleteByRubric_Id(rubric.getId());
            rubricRepository.delete(rubric);
        });
        courseLecturerRepository.deleteByCourse_Id(id);
        chapterRepository.deleteAll(chapters);
        courseRepository.delete(course);
        log.info("Deleted course: id={}, code={}", id, course.getCourseCode());
    }

    private Course findCourse(String id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND, "Không tìm thấy môn học: " + id));
    }

    private static String normalizeCode(String code) {
        return code.trim().toUpperCase();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private CourseResponse toResponse(Course course) {
        return CourseResponse.builder()
                .id(course.getId())
                .courseCode(course.getCourseCode())
                .name(course.getName())
                .description(course.getDescription())
                .chapterCount((int) chapterRepository.countByCourse_Id(course.getId()))
                .build();
    }
}
