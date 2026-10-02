package com.aives.modules.auth.controller;

import com.aives.common.dto.ApiResponse;
import com.aives.common.security.UserPrincipal;
import com.aives.modules.auth.dto.CreateUserRequest;
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

    @Operation(summary = "Tạo account mới (ADMIN / LECTURER / STUDENT). Lecturer chỉ tạo được ở đây")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.ok("Tạo account thành công", userService.createUser(request));
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
