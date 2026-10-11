package com.aives.modules.auth.controller;

import com.aives.common.dto.ApiResponse;
import com.aives.common.security.UserPrincipal;
import com.aives.modules.auth.dto.CreateUserRequest;
import com.aives.modules.auth.dto.CreatedUserResponse;
import com.aives.modules.auth.dto.UpdateUserStatusRequest;
import com.aives.modules.auth.dto.UserResponse;
import com.aives.modules.auth.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin - Users", description = "Quản lý tài khoản (chỉ ADMIN)")
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;

    @Operation(summary = "Danh sách tất cả account")
    @GetMapping
    public ApiResponse<List<UserResponse>> getAllUsers() {
        return ApiResponse.ok(userService.getAllUsers());
    }

    @Operation(summary = "Tạo account mới (ADMIN / LECTURER / STUDENT). Hệ thống tự sinh mật khẩu tạm và gửi tới email của người dùng")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreatedUserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        CreatedUserResponse created = userService.createUser(request);
        String message = created.isEmailSent()
                ? "Tạo account thành công, thông tin đăng nhập đã được gửi tới " + created.getUser().getEmail()
                : "Tạo account thành công nhưng hệ thống chưa bật gửi mail: mật khẩu tạm chỉ được in ở console của backend";
        return ApiResponse.ok(message, created);
    }

    @Operation(summary = "Vô hiệu hoá / kích hoạt lại account theo id (active = false là vô hiệu hoá)")
    @PutMapping("/{id}/status")
    public ApiResponse<UserResponse> updateUserStatus(
            @PathVariable String id,
            @Valid @RequestBody UpdateUserStatusRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        boolean active = request.getActive();
        UserResponse user = userService.updateUserStatus(id, active, principal.getId());
        return ApiResponse.ok(active ? "Đã kích hoạt lại account" : "Đã vô hiệu hoá account", user);
    }

    @Operation(summary = "Xoá account theo id (vd: ST482913)")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteUser(
            @PathVariable String id,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        userService.deleteUser(id, principal.getId());
        return ApiResponse.ok("Xoá account thành công", null);
    }
}
