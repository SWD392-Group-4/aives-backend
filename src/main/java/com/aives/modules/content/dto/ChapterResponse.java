package com.aives.modules.content.dto;

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
public class ChapterResponse {

    private String id;
    private String courseId;
    private String name;
    private Integer orderNo;
    private String description;
    private Integer materialCount;
    private Integer questionCount;
}