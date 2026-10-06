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
public class SkipQuestionRequest {

    @Schema(description = "Id luot hoi muon bo qua (exchangeId trong response truoc do)", example = "IE0482913375")
    @NotBlank(message = "Thiếu mã câu hỏi muốn bỏ qua")
    @Size(max = 20, message = "Mã câu hỏi không hợp lệ")
    private String exchangeId;
}
