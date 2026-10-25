package com.aives.modules.content.service;

import com.aives.common.security.UserPrincipal;
import com.aives.modules.content.dto.CourseRubricResponse;
import com.aives.modules.content.dto.UpdateCourseRubricRequest;

public interface CourseRubricService {

    CourseRubricResponse getRubricByCourseId(String courseId);

    CourseRubricResponse updateRubric(String courseId, UpdateCourseRubricRequest request, UserPrincipal principal);

    boolean isRubricReadyForGrading(String courseId);
}
