package com.aives.modules.auth.dto;

import com.aives.modules.auth.enums.RoleName;
import java.time.LocalDateTime;
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
public class UserResponse {

    private String id;
    private String email;
    private String fullName;
    private RoleName role;
    private boolean active;
    // true: dang dung mat khau tam do admin cap, FE phai dua user toi man hinh doi mat khau
    private boolean mustChangePassword;
    private LocalDateTime createdAt;
}
