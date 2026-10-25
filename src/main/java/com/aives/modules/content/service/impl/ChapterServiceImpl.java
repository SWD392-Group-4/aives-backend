package com.aives.modules.content.service.impl;

import com.aives.common.exception.BusinessException;
import com.aives.common.exception.ErrorCode;
import com.aives.common.util.RandomIdGenerator;
import com.aives.modules.content.dto.ChapterResponse;
import com.aives.modules.content.dto.CreateChapterRequest;
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
