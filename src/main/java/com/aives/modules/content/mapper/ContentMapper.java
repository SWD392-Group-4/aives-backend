package com.aives.modules.content.mapper;

import com.aives.common.util.AuditTime;
import com.aives.modules.content.dto.QuestionResponse;
import com.aives.modules.content.dto.RubricCriterionResponse;
import com.aives.modules.content.dto.RubricResponse;
import com.aives.modules.content.dto.TopicResponse;
import com.aives.modules.content.entity.Question;
import com.aives.modules.content.entity.Rubric;
import com.aives.modules.content.entity.RubricCriterion;
import com.aives.modules.content.entity.Topic;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ContentMapper {

    private final ObjectMapper objectMapper;

    /** topic.getLesson() va question.getTopic() / getRubric() phai da duoc nap (join fetch hoac dang trong transaction). */
    public TopicResponse toTopicResponse(Topic topic, int questionCount) {
        return toTopicResponse(topic, questionCount, 0);
    }

    public TopicResponse toTopicResponse(Topic topic, int questionCount, int approvedQuestionCount) {
        return TopicResponse.builder()
                .id(topic.getId())
                .lessonId(topic.getLesson().getId())
                .lessonTitle(topic.getLesson().getTitle())
                .name(topic.getName())
                .description(topic.getDescription())
                .questionCount(questionCount)
                .approvedQuestionCount(approvedQuestionCount)
                .build();
    }

    public RubricResponse toRubricResponse(Rubric rubric, List<RubricCriterion> criteria) {
        List<RubricCriterionResponse> items = new ArrayList<>();
        for (RubricCriterion criterion : criteria) {
            items.add(RubricCriterionResponse.builder()
                    .id(criterion.getId())
                    .rubricId(rubric.getId())
                    .name(criterion.getName())
                    .description(criterion.getDescription())
                    .weightPercent(criterion.getWeightPercent())
                    .orderNo(criterion.getOrderNo())
                    .build());
        }
        return RubricResponse.builder()
                .id(rubric.getId())
                .createdBy(rubric.getCreatedBy())
                .name(rubric.getName())
                .description(rubric.getDescription())
                .maxScore(rubric.getMaxScore())
                .criteria(items)
                .createdAt(toInstant(rubric.getCreatedAt()))
                .build();
    }

    public QuestionResponse toQuestionResponse(Question question) {
        return QuestionResponse.builder()
                .id(question.getId())
                .topicId(question.getTopic().getId())
                .topicName(question.getTopic().getName())
                .lessonTitle(question.getTopic().getLesson().getTitle())
                .rubricId(question.getRubric() == null ? null : question.getRubric().getId())
                .rubricName(question.getRubric() == null ? null : question.getRubric().getName())
                .content(question.getContent())
                .bloomLevel(question.getBloomLevel())
                .modelAnswer(question.getModelAnswer())
                .expectedKeywords(parseKeywords(question.getExpectedKeywords()))
                .source(question.getSource())
                .status(question.getStatus())
                .createdAt(toInstant(question.getCreatedAt()))
                .updatedAt(toInstant(question.getUpdatedAt()))
                .build();
    }

    /** expected_keywords luu dang mang JSON ("["a","b"]"); du lieu cu co the la chuoi cach nhau bang dau phay. */
    public List<String> parseKeywords(String stored) {
        if (stored == null || stored.isBlank()) {
            return List.of();
        }
        String trimmed = stored.trim();
        if (trimmed.startsWith("[")) {
            try {
                return objectMapper.readValue(trimmed, new TypeReference<List<String>>() { });
            } catch (Exception ignored) {
                // Khong phai JSON hop le thi tach theo dau phay ben duoi
            }
        }
        List<String> keywords = new ArrayList<>();
        for (String part : trimmed.split(",")) {
            if (!part.isBlank()) {
                keywords.add(part.trim());
            }
        }
        return keywords;
    }

    public String toKeywordsJson(List<String> keywords) {
        try {
            return objectMapper.writeValueAsString(keywords == null ? List.of() : keywords);
        } catch (Exception ex) {
            return "[]";
        }
    }

    /** Chi dung cho created_at / updated_at (gio cua may chu, xem AuditTime). */
    public Instant toInstant(LocalDateTime auditDateTime) {
        return AuditTime.toInstant(auditDateTime);
    }
}
