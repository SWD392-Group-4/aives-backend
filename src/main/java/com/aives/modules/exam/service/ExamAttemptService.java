package com.aives.modules.exam.service;

import com.aives.modules.exam.dto.ExamAttemptResponse;
import com.aives.modules.exam.dto.JoinExamRequest;

/**
 * Sinh vien vao phien thi bang ma phien + ma truy cap va xem luot thi cua minh.
 */
public interface ExamAttemptService {

    ExamAttemptResponse joinSession(String studentId, JoinExamRequest request);

    ExamAttemptResponse getAttempt(String attemptId, String studentId);
}
