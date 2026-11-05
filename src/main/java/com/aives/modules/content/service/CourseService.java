package com.aives.modules.content.service;

import com.aives.modules.content.dto.CourseResponse;
import com.aives.modules.content.dto.CreateCourseRequest;
import com.aives.modules.content.dto.UpdateCourseRequest;
import java.util.List;

public interface CourseService {
    List<CourseResponse> getAllCourses();

    CourseResponse getCourseById(String id);

    CourseResponse createCourse(CreateCourseRequest request);

    CourseResponse updateCourse(String id, UpdateCourseRequest request);

    void deleteCourse(String id);
}
