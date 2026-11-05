package com.aives.modules.content.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Sinh ngan hang cau hoi cho 1 mon: chon cac chuong va so cau moi chuong. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GenerateCourseQuestionsRequest {

    @Schema(description = "Các chương được chọn và số câu hỏi cần sinh cho mỗi chương")
    @NotEmpty(message = "Hãy chọn ít nhất 1 chương")
    @Valid
    private List<ChapterQuota> chapters;

    @Schema(description = "Tỉ lệ Dễ / Trung bình / Khó, tổng 100%")
    @NotNull(message = "Tỉ lệ độ khó không được để trống")
    @Valid
    private DifficultyRatioConfig difficultyRatio;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ChapterQuota {

        @NotBlank(message = "Thiếu mã chương")
        private String chapterId;

        @NotNull(message = "Thiếu số câu hỏi")
        @Min(value = 1, message = "Mỗi chương sinh ít nhất 1 câu")
        @Max(value = 20, message = "Mỗi chương sinh tối đa 20 câu mỗi lần")
        private Integer count;
    }
}
