package com.aives.modules.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
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
public class ResetPasswordRequest {

    @Schema(example = "student09@fpt.edu.vn", description = "Email tài khoản")
    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không đúng định dạng")
    @Size(max = 255, message = "Email tối đa 255 ký tự")
    private String email;

    @Schema(example = "123456", description = "Mã OTP 6 số đã gửi qua email")
    @NotBlank(message = "Mã OTP không được để trống")
    @Pattern(regexp = "^[0-9]{6}$", message = "Mã OTP gồm đúng 6 chữ số")
    private String otpCode;

    @Schema(example = "NewPassword123", description = "Mật khẩu mới")
    @NotBlank(message = "Mật khẩu mới không được để trống")
    @Size(min = 6, max = 72, message = "Mật khẩu mới từ 6 đến 72 ký tự")
    private String newPassword;
}
