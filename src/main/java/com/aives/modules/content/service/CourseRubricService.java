package com.aives.modules.content.service;

import com.aives.common.security.UserPrincipal;
import com.aives.modules.content.dto.CourseRubricResponse;
import com.aives.modules.content.dto.UpdateCourseRubricRequest;

public interface CourseRubricService {

    CourseRubricResponse getRubricByCourseId(String courseId);

    CourseRubricResponse updateRubric(String courseId, UpdateCourseRubricRequest request, UserPrincipal principal);

    boolean isRubricReadyForGrading(String courseId);

    /** Duyet rubric (tong trong so phai dung 100%). Sua rubric sau do se tro ve trang thai nhap. */
    CourseRubricResponse approveRubric(String courseId, UserPrincipal principal);
}
