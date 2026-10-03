package com.aives.modules.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Admin vo hieu hoa / kich hoat lai 1 account.
 * active = false: account khong dang nhap duoc nua, token dang dung cung bi tu choi.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserStatusRequest {

    @Schema(example = "false", description = "false = vô hiệu hoá, true = kích hoạt lại")
    @NotNull(message = "Trạng thái không được để trống")
    private Boolean active;
}
