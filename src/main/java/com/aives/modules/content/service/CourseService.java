package com.aives.modules.content.service;

import com.aives.modules.content.dto.CourseResponse;
import java.util.List;

public interface CourseService {
    List<CourseResponse> getAllCourses();
    CourseResponse getCourseById(String id);
}
