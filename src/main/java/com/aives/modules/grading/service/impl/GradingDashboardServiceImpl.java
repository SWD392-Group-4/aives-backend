package com.aives.modules.grading.service.impl;

import com.aives.common.security.UserPrincipal;
import com.aives.modules.auth.enums.RoleName;
import com.aives.modules.exam.enums.AttemptStatus;
import com.aives.modules.exam.repository.VivaAttemptRepository;
import com.aives.modules.exam.repository.VivaExamRepository;
import com.aives.modules.grading.dto.LecturerDashboardResponse;
import com.aives.modules.grading.dto.StudentDashboardResponse;
import com.aives.modules.grading.repository.GradeAppealRepository;
import com.aives.modules.grading.service.GradingDashboardService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GradingDashboardServiceImpl implements GradingDashboardService {

    private final VivaExamRepository vivaExamRepository;
    private final VivaAttemptRepository vivaAttemptRepository;
    private final GradeAppealRepository gradeAppealRepository;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public LecturerDashboardResponse getLecturerDashboard(UserPrincipal actor) {
        LocalDateTime now = LocalDateTime.now(clock);
        boolean isAdmin = actor.getRole() == RoleName.ADMIN;

        long openSessions = isAdmin
                ? vivaExamRepository.countOngoingSessions(now)
                : vivaExamRepository.countOngoingSessionsByLecturer(actor.getId(), now);

        long pendingReviewAttempts = isAdmin
                ? vivaAttemptRepository.countPendingReviewAll()
                : vivaAttemptRepository.countPendingReviewByLecturer(actor.getId());

        long pendingAppeals = isAdmin
                ? gradeAppealRepository.countPendingAll()
                : gradeAppealRepository.countPendingByLecturer(actor.getId());

        return LecturerDashboardResponse.builder()
                .openSessions(openSessions)
                .pendingReviewAttempts(pendingReviewAttempts)
                .pendingAppeals(pendingAppeals)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public StudentDashboardResponse getStudentDashboard(UserPrincipal actor) {
        LocalDateTime now = LocalDateTime.now(clock);

        long upcomingOrOngoingSessions = vivaExamRepository.countUpcomingOrOngoingSessions(now);
        long completedAttempts = vivaAttemptRepository.countByStudentIdAndStatus(actor.getId(), AttemptStatus.COMPLETED);

        Double avgScore = vivaAttemptRepository.getAveragePublishedScoreByStudentId(actor.getId());
        Double roundedScore = null;
        if (avgScore != null) {
            roundedScore = BigDecimal.valueOf(avgScore)
                    .setScale(2, RoundingMode.HALF_UP)
                    .doubleValue();
        }

        long pendingAppeals = gradeAppealRepository.countPendingByStudentId(actor.getId());

        return StudentDashboardResponse.builder()
                .upcomingOrOngoingSessions(upcomingOrOngoingSessions)
                .completedAttempts(completedAttempts)
                .averageScore(roundedScore)
                .pendingAppeals(pendingAppeals)
                .build();
    }
}
