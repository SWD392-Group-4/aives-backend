package com.aives.modules.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Ket qua khi admin tao tai khoan.
 * emailSent = false nghia la he thong chua bat gui mail (app.mail.enabled = false):
 * mat khau tam chi duoc in ra console cua backend, KHONG toi hop thu cua nguoi dung.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreatedUserResponse {

    private UserResponse user;
    private boolean emailSent;
}
