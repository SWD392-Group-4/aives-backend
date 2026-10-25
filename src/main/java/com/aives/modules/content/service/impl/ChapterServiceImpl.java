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
import com.aives.modules.content.service.IChapterService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChapterServiceImpl implements IChapterService {

    private final CourseRepository courseRepository;
    private final ChapterRepository chapterRepository;
    private final MaterialRepository materialRepository;
    private final QuestionRepository questionRepository;
    private final RandomIdGenerator idGenerator;

    @Override
    @Transactional(readOnly = true)
    public List<ChapterResponse> getChaptersByCourseId(String courseId) {
        log.info("Lay danh sach chuong cua mon hoc: courseId={}", courseId);
        if (!courseRepository.existsById(courseId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy môn học " + courseId);
        }

        List<Chapter> chapters = chapterRepository.findByCourse_IdOrderByOrderNoAsc(courseId);
        return chapters.stream().map(this::mapToResponse).toList();
    }

    @Override
    @Transactional
    public ChapterResponse createChapter(String courseId, CreateChapterRequest request) {
        log.info("Tao chuong moi cho mon hoc: courseId={}, name={}", courseId, request.getName());
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy môn học " + courseId));

        Chapter chapter = Chapter.builder()
                .course(course)
                .name(request.getName())
                .orderNo(request.getOrderNo())
                .description(request.getDescription())
                .build();
        chapter.setId(idGenerator.next("CH"));

        chapter = chapterRepository.save(chapter);
        return mapToResponse(chapter);
    }

    private ChapterResponse mapToResponse(Chapter chapter) {
        int matCount = materialRepository.countByChapter_Id(chapter.getId());
        int qCount = questionRepository.countByChapter_Id(chapter.getId());

        return ChapterResponse.builder()
                .id(chapter.getId())
                .courseId(chapter.getCourse().getId())
                .name(chapter.getName())
                .orderNo(chapter.getOrderNo())
                .description(chapter.getDescription())
                .materialCount(matCount)
                .questionCount(qCount)
                .build();
    }
}