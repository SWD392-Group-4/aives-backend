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
public class ChapterResponse {

    @Schema(example = "CHAP_01", description = "Mã chương")
    private String id;

    @Schema(example = "SWD392", description = "Mã môn học")
    private String courseId;

    @Schema(example = "Chương 1 - Clean Architecture & SOLID", description = "Tên chương")
    private String name;

    @Schema(example = "1", description = "Thứ tự sắp xếp chương")
    private Integer orderNo;

    @Schema(example = "Tổng quan kiến trúc sạch", description = "Mô tả chương")
    private String description;

    @Schema(example = "3", description = "Số lượng tài liệu/slide của chương")
    private Integer materialCount;

    @Schema(example = "25", description = "Số lượng câu hỏi trong ngân hàng của chương")
    private Integer questionCount;
}
