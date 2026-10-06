package com.aives.modules.content.dto;

import java.math.BigDecimal;
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
public class RubricCriterionResponse {
    private String id;
    private String rubricId;
    private String name;
    private String description;
    // Trong so tinh theo %, tong cac tieu chi cua 1 rubric = 100
    private BigDecimal weightPercent;
    private int orderNo;
}
