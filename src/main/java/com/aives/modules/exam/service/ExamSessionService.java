package com.aives.modules.exam.service;

import com.aives.common.dto.PageResponse;
import com.aives.common.security.UserPrincipal;
import com.aives.modules.exam.dto.DeleteExamSessionResponse;
import com.aives.modules.exam.dto.ExamSessionRequest;
import com.aives.modules.exam.dto.ExamSessionResponse;

/**
 * Quan ly phien thi, danh cho LECTURER (chi phien cua minh) va ADMIN (moi phien).
 */
public interface ExamSessionService {

    ExamSessionResponse createSession(ExamSessionRequest request, UserPrincipal actor);

    PageResponse<ExamSessionResponse> getSessions(UserPrincipal actor, String status, String keyword, int page, int size);

    ExamSessionResponse getSession(String sessionId, UserPrincipal actor);

    ExamSessionResponse updateSession(String sessionId, ExamSessionRequest request, UserPrincipal actor);

    DeleteExamSessionResponse deleteSession(String sessionId, UserPrincipal actor);

    ExamSessionResponse regeneratePasscode(String sessionId, UserPrincipal actor);
}
