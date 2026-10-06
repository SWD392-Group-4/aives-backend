package com.aives.modules.content.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class QuestionStatusRequest {

    @Schema(example = "APPROVED")
    @NotBlank(message = "Thiếu trạng thái mới")
    @Pattern(regexp = "DRAFT|APPROVED|REJECTED|ARCHIVED", message = "Trạng thái phải là DRAFT, APPROVED, REJECTED hoặc ARCHIVED")
    private String status;

    @Schema(description = "Rubric gan cho cau hoi khi duyet. Bo trong = giu rubric dang gan.")
    @Size(max = 20, message = "Mã rubric không hợp lệ")
    private String rubricId;
}
