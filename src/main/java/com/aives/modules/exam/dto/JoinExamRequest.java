package com.aives.modules.exam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class JoinExamRequest {

    @Schema(example = "AIVES_EXAM_2026_049282")
    @NotBlank(message = "Mã phiên thi không được để trống")
    @Size(max = 40, message = "Mã phiên thi không đúng định dạng")
    private String examId;

    @Schema(example = "123456")
    @NotBlank(message = "Mã truy cập không được để trống")
    @Size(max = 10, message = "Mã truy cập gồm đúng 6 chữ số")
    private String passcode;

    @Schema(example = "WEB", description = "Nền tảng của thiết bị: WEB | MOBILE (mặc định WEB)")
    private String platform;
}
