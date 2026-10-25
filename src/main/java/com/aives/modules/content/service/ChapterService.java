package com.aives.modules.content.service;

import com.aives.modules.content.dto.ChapterResponse;
import com.aives.modules.content.dto.CreateChapterRequest;
import java.util.List;

public interface ChapterService {

    List<ChapterResponse> getChaptersByCourseId(String courseId);

    ChapterResponse createChapter(String courseId, CreateChapterRequest request);
}
