package com.aives.modules.auth.dto;

import com.aives.modules.auth.enums.RoleName;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Admin tao tai khoan. KHONG co mat khau: he thong tu sinh mat khau tam
 * va gui toi email cua nguoi dung.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserRequest {

    @Schema(example = "lecturer03@fe.edu.vn", description = "Mật khẩu tạm sẽ được gửi tới email này")
    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không đúng định dạng")
    @Size(max = 255, message = "Email tối đa 255 ký tự")
    private String email;

    @Schema(example = "Nguyễn Văn A")
    @NotBlank(message = "Họ tên không được để trống")
    @Size(max = 150, message = "Họ tên tối đa 150 ký tự")
    private String fullName;

    @Schema(example = "LECTURER")
    @NotNull(message = "Role không được để trống")
    private RoleName role;
}
