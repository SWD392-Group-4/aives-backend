package com.aives.modules.grading.service;

import com.aives.common.security.UserPrincipal;
import com.aives.modules.grading.dto.LecturerDashboardResponse;
import com.aives.modules.grading.dto.StudentDashboardResponse;

public interface GradingDashboardService {

    LecturerDashboardResponse getLecturerDashboard(UserPrincipal actor);

    StudentDashboardResponse getStudentDashboard(UserPrincipal actor);
}
