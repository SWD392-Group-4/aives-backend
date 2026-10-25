package com.aives.modules.content.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Thông tin môn học")
public class CourseResponse {

    @Schema(example = "CRS_SWD392")
    private String id;

    @Schema(example = "SWD392")
    private String courseCode;

    @Schema(example = "Software Architecture and Design")
    private String name;

    @Schema(example = "Môn học Kiến trúc và Thiết kế Phần mềm")
    private String description;
}
