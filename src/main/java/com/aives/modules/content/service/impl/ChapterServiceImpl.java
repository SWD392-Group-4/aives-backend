package com.aives.modules.content.service.impl;

import com.aives.common.exception.BusinessException;
import com.aives.common.exception.ErrorCode;
import com.aives.common.util.RandomIdGenerator;
import com.aives.modules.content.dto.ChapterResponse;
import com.aives.modules.content.dto.CreateChapterRequest;
import com.aives.modules.content.dto.UpdateChapterRequest;
import com.aives.modules.content.entity.Chapter;
import com.aives.modules.content.entity.Course;
import com.aives.modules.content.repository.ChapterRepository;
import com.aives.modules.content.repository.CourseRepository;
import com.aives.modules.content.repository.MaterialRepository;
import com.aives.modules.content.repository.QuestionRepository;
import com.aives.modules.content.service.ChapterService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChapterServiceImpl implements ChapterService {

    private final CourseRepository courseRepository;
    private final ChapterRepository chapterRepository;
    private final MaterialRepository materialRepository;
    private final QuestionRepository questionRepository;
    private final RandomIdGenerator idGenerator;

    @Override
    @Transactional(readOnly = true)
    public List<ChapterResponse> getChaptersByCourseId(String courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND, "Không tìm thấy môn học: " + courseId));

        List<Chapter> chapters = chapterRepository.findByCourse_IdOrderByOrderNoAsc(course.getId());
        return chapters.stream()
                .map(this::toChapterResponse)
                .toList();
    }

    @Override
    @Transactional
    public ChapterResponse createChapter(String courseId, CreateChapterRequest request) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND, "Không tìm thấy môn học: " + courseId));

        Chapter chapter = Chapter.builder()
                .course(course)
                .name(request.getName().trim())
                .orderNo(request.getOrderNo() != null ? request.getOrderNo() : 1)
                .description(request.getDescription())
                .build();
        chapter.setId(idGenerator.next("CHAP"));

        Chapter saved = chapterRepository.save(chapter);
        log.info("Created new Chapter: id={}, courseId={}, name={}", saved.getId(), course.getId(), saved.getName());
        return toChapterResponse(saved);
    }

    @Override
    @Transactional
    public ChapterResponse updateChapter(String courseId, String chapterId, UpdateChapterRequest request) {
        Chapter chapter = findChapterOfCourse(courseId, chapterId);
        chapter.setName(request.getName().trim());
        chapter.setOrderNo(request.getOrderNo() != null ? request.getOrderNo() : chapter.getOrderNo());
        chapter.setDescription(request.getDescription());

        Chapter saved = chapterRepository.save(chapter);
        log.info("Updated Chapter: id={}, courseId={}, name={}", saved.getId(), courseId, saved.getName());
        return toChapterResponse(saved);
    }

    /** Chi xoa duoc chuong chua co tai lieu va cau hoi. */
    @Override
    @Transactional
    public void deleteChapter(String courseId, String chapterId) {
        Chapter chapter = findChapterOfCourse(courseId, chapterId);

        long materialCount = materialRepository.countByChapterId(chapterId);
        long questionCount = questionRepository.countByChapter_Id(chapterId);
        if (materialCount > 0 || questionCount > 0) {
            throw new BusinessException(ErrorCode.DATA_CONFLICT,
                    "Chương đang có " + materialCount + " tài liệu và " + questionCount
                            + " câu hỏi. Hãy xoá tài liệu và câu hỏi trước khi xoá chương");
        }

        chapterRepository.delete(chapter);
        log.info("Deleted Chapter: id={}, courseId={}", chapterId, courseId);
    }

    private Chapter findChapterOfCourse(String courseId, String chapterId) {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAPTER_NOT_FOUND, "Không tìm thấy chương: " + chapterId));
        if (!chapter.getCourse().getId().equals(courseId)) {
            throw new BusinessException(ErrorCode.CHAPTER_NOT_FOUND, "Chương " + chapterId + " không thuộc môn học " + courseId);
        }
        return chapter;
    }

    private ChapterResponse toChapterResponse(Chapter chapter) {
        int materialCount = (int) materialRepository.countByChapterId(chapter.getId());
        int questionCount = (int) questionRepository.countByChapter_Id(chapter.getId());

        return ChapterResponse.builder()
                .id(chapter.getId())
                .courseId(chapter.getCourse().getId())
                .name(chapter.getName())
                .orderNo(chapter.getOrderNo())
                .description(chapter.getDescription())
                .materialCount(materialCount)
                .questionCount(questionCount)
                .build();
    }
}
