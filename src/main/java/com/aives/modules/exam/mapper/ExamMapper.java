package com.aives.modules.exam.mapper;

import com.aives.modules.exam.dto.ExamAttemptResponse;
import com.aives.modules.exam.dto.ExamSessionResponse;
import com.aives.modules.exam.entity.VivaAttempt;
import com.aives.modules.exam.entity.VivaExam;
import com.aives.modules.exam.enums.AttemptStatus;
import com.aives.modules.exam.enums.ExamSessionStatus;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ExamMapper {

    /** Trang thai phien tai thoi diem now (gio UTC). */
    public ExamSessionStatus statusOf(VivaExam exam, LocalDateTime now) {
        if (exam.isCancelled()) {
            return ExamSessionStatus.CANCELLED;
        }
        if (now.isBefore(exam.getStartAt())) {
            return ExamSessionStatus.UPCOMING;
        }
        if (now.isBefore(exam.getEndAt())) {
            return ExamSessionStatus.ONGOING;
        }
        return ExamSessionStatus.ENDED;
    }

    /** topicNames: ten cac chu de theo id (chu de da bi xoa thi khong co trong map va bi bo qua). */
    public ExamSessionResponse toResponse(VivaExam exam, String ownerName, long attemptCount,
                                          Map<String, String> topicNames, LocalDateTime now) {
        return toResponse(exam, ownerName, attemptCount, topicNames, null, null, now);
    }

    public ExamSessionResponse toResponse(VivaExam exam, String ownerName, long attemptCount,
                                          Map<String, String> topicNames, String courseName,
                                          List<com.aives.modules.exam.entity.VivaExamQuestion> examQuestions,
                                          LocalDateTime now) {
        ExamSessionStatus status = statusOf(exam, now);
        List<ExamSessionResponse.TopicItem> topics = new ArrayList<>();
        if (exam.getTopicIds() != null) {
            for (String topicId : exam.getTopicIds()) {
                String name = topicNames != null ? topicNames.get(topicId) : null;
                if (name != null) {
                    topics.add(new ExamSessionResponse.TopicItem(topicId, name));
                }
            }
            topics.sort(Comparator.comparing(ExamSessionResponse.TopicItem::getName));
        }

        List<ExamSessionResponse.QuestionItem> questionItems = null;
        if (examQuestions != null && !examQuestions.isEmpty()) {
            questionItems = examQuestions.stream()
                    .map(eq -> ExamSessionResponse.QuestionItem.builder()
                            .id(eq.getQuestion().getId())
                            .content(eq.getQuestion().getContent())
                            .difficulty(eq.getQuestion().getDifficulty())
                            .orderNo(eq.getOrderNo())
                            .build())
                    .toList();
        }

        return ExamSessionResponse.builder()
                .id(exam.getId())
                .passcode(exam.getPasscode())
                .title(exam.getTitle())
                .description(exam.getDescription())
                .startAt(toInstant(exam.getStartAt()))
                .endAt(toInstant(exam.getEndAt()))
                .durationMinutes(exam.getDurationMinutes())
                .maxAttempts(exam.getMaxAttempts())
                .allowMobileExam(Boolean.TRUE.equals(exam.getAllowMobileExam()))
                .status(status)
                .courseId(exam.getCourseId())
                .courseName(courseName)
                .questions(questionItems)
                .questionCount(exam.getQuestionCount())
                .topics(topics)
                .ownerId(exam.getLecturerId())
                .ownerName(ownerName)
                .attemptCount(attemptCount)
                .scheduleLocked(status != ExamSessionStatus.UPCOMING)
                .createdAt(exam.getCreatedAt())
                .build();
    }

    /** exam duoc truyen rieng vi attempt.getExam() la LAZY, co the chua duoc nap. */
    public ExamAttemptResponse toAttemptResponse(VivaAttempt attempt, VivaExam exam, LocalDateTime now) {
        // Da ket thuc (sinh vien bam ket thuc som) thi khong con thoi gian, du chua toi han
        long remainingSeconds = attempt.getStatus() == AttemptStatus.COMPLETED
                ? 0
                : Math.max(Duration.between(now, attempt.getDeadlineAt()).getSeconds(), 0);
        // Het gio thi coi nhu da xong, ke ca khi DB chua kip cap nhat
        AttemptStatus status = remainingSeconds == 0 ? AttemptStatus.COMPLETED : attempt.getStatus();
        LocalDateTime completedAt = attempt.getCompletedAt() != null
                ? attempt.getCompletedAt()
                : (status == AttemptStatus.COMPLETED ? attempt.getDeadlineAt() : null);
        return ExamAttemptResponse.builder()
                .attemptId(attempt.getId())
                .examId(exam.getId())
                .title(exam.getTitle())
                .durationMinutes(exam.getDurationMinutes())
                .attemptNo(attempt.getAttemptNo())
                .maxAttempts(exam.getMaxAttempts())
                .status(status)
                .startedAt(toInstant(attempt.getStartedAt()))
                .deadlineAt(toInstant(attempt.getDeadlineAt()))
                .completedAt(completedAt == null ? null : toInstant(completedAt))
                .serverTime(toInstant(now))
                .remainingSeconds(remainingSeconds)
                .build();
    }

    private Instant toInstant(LocalDateTime utcDateTime) {
        return utcDateTime.toInstant(ZoneOffset.UTC);
    }
}
