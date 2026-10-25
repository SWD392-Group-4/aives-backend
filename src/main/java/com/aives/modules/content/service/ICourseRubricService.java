package com.aives.modules.content.service;

import com.aives.modules.content.dto.CourseRubricResponse;
import com.aives.modules.content.dto.UpdateCourseRubricRequest;

public interface ICourseRubricService {

    CourseRubricResponse getRubricByCourseId(String courseId);

    CourseRubricResponse updateRubric(String courseId, UpdateCourseRubricRequest request, String lecturerId);

    boolean isRubricReadyForGrading(String courseId);
}