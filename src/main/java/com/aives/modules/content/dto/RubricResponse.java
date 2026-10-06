package com.aives.modules.content.dto;

import java.math.BigDecimal;
import java.time.Instant;
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
public class RubricResponse {
    private String id;
    private String createdBy;
    private String name;
    private String description;
    private BigDecimal maxScore;
    private List<RubricCriterionResponse> criteria;
    private Instant createdAt;
}
