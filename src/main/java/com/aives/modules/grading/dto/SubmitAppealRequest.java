package com.aives.modules.grading.dto;

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
public class SubmitAppealRequest {

    @Schema(description = "Id diem cau hoi muon phuc khao (questionGrades[].id trong ket qua)", example = "QG0482913375")
    @NotBlank(message = "Thiếu câu hỏi muốn phúc khảo")
    @Size(max = 20, message = "Mã điểm câu hỏi không hợp lệ")
    private String questionGradeId;

    @NotBlank(message = "Vui lòng nêu lý do phúc khảo")
    @Size(min = 15, max = 2000, message = "Lý do phúc khảo dài từ 15 đến 2000 ký tự")
    private String reason;
}
