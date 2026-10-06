package com.aives.modules.content.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ket qua doc 1 file giang vien gui len.
 * File cau hoi: lesson, topic, questions (NHAP) va rubric da chon (neu co). File rubric: chi co rubrics.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContentImportResponse {
    private String fileName;
    private LessonResponse lesson;
    private TopicResponse topic;
    private List<RubricResponse> rubrics;
    private List<QuestionResponse> questions;
}
