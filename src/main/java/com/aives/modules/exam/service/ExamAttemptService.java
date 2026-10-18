package com.aives.modules.exam.service;

import com.aives.modules.exam.dto.ExamAttemptResponse;
import com.aives.modules.exam.dto.JoinExamRequest;

/**
 * Sinh vien vao phien thi bang ma phien + ma truy cap va xem luot thi cua minh.
 */
public interface ExamAttemptService {

    ExamAttemptResponse joinSession(String studentId, JoinExamRequest request, String platform);

    default ExamAttemptResponse joinSession(String studentId, JoinExamRequest request) {
        return joinSession(studentId, request, request.getPlatform() != null ? request.getPlatform() : "WEB");
    }

    ExamAttemptResponse getAttempt(String attemptId, String studentId);

    /** Sinh vien chu dong ket thuc bai thi truoc khi het gio. Goi lai nhieu lan van tra ve ket qua nhu lan dau. */
    ExamAttemptResponse finishAttempt(String attemptId, String studentId);
}
