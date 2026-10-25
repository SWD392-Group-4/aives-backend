package com.aives.modules.content.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourseRubricResponse {

    private String id;
    private String courseId;
    private String name;
    private String description;
    private BigDecimal maxScore;
    private Boolean isLocked;
    private List<RubricCriteriaDto> criteria;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}