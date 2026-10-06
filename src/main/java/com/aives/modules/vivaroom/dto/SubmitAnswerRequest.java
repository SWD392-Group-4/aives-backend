package com.aives.modules.vivaroom.dto;

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
public class SubmitAnswerRequest {

    @Schema(description = "Id luot hoi dang tra loi (exchangeId trong response truoc do)", example = "IE0482913375")
    @NotBlank(message = "Thiếu mã câu hỏi đang trả lời")
    @Size(max = 20, message = "Mã câu hỏi không hợp lệ")
    private String exchangeId;

    @Schema(description = "Noi dung cau tra loi (chu). De trong = sinh vien khong tra loi")
    @Size(max = 8000, message = "Câu trả lời dài tối đa 8000 ký tự")
    private String transcript;
}
