package com.aives.modules.content.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
public class UpdateChapterRequest {

    @Schema(example = "Chương 2 - Microservices Communication (đã sửa)", description = "Tên chương")
    @NotBlank(message = "Tên chương không được để trống")
    @Size(max = 200, message = "Tên chương tối đa 200 ký tự")
    private String name;

    @Schema(example = "2", description = "Thứ tự sắp xếp chương")
    @NotNull(message = "Thứ tự sắp xếp không được để trống")
    @Min(value = 1, message = "Thứ tự sắp xếp phải lớn hơn hoặc bằng 1")
    private Integer orderNo;

    @Schema(example = "Giao tiếp REST và gRPC giữa các services", description = "Mô tả chương")
    private String description;
}
