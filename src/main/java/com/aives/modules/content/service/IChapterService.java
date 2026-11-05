package com.aives.modules.content.service;

import com.aives.modules.content.dto.ChapterResponse;
import com.aives.modules.content.dto.CreateChapterRequest;
import com.aives.modules.content.dto.UpdateChapterRequest;
import java.util.List;

public interface IChapterService {

    List<ChapterResponse> getChaptersByCourseId(String courseId);

    ChapterResponse createChapter(String courseId, CreateChapterRequest request);

    ChapterResponse updateChapter(String courseId, String chapterId, UpdateChapterRequest request);

    void deleteChapter(String courseId, String chapterId);
}