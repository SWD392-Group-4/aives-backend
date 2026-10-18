package com.aives.modules.auth.service;

import com.aives.common.dto.PageResponse;
import com.aives.modules.auth.dto.ChangePasswordRequest;
import com.aives.modules.auth.dto.CreateUserRequest;
import com.aives.modules.auth.dto.CreatedUserResponse;
import com.aives.modules.auth.dto.RegisterRequest;
import com.aives.modules.auth.dto.UpdateProfileRequest;
import com.aives.modules.auth.dto.UserResponse;
import com.aives.modules.auth.enums.RoleName;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface UserService {

    PageResponse<UserResponse> getUsers(RoleName role, String keyword, int page, int size);

    List<UserResponse> getAllUsers();

    UserResponse getUserById(String userId);

    /** Tra ve map id -> ho ten. Id khong ton tai thi khong co trong map. Dung cho module khac hien ten user. */
    Map<String, String> getFullNamesByIds(Collection<String> userIds);

    /** Tra ve map id -> thong tin user (ho ten, email...). Id khong ton tai thi khong co trong map. */
    Map<String, UserResponse> getUsersByIds(Collection<String> userIds);

    /** Sinh vien tu dang ky: role luon la STUDENT, mat khau do nguoi dung tu dat. */
    UserResponse registerStudent(RegisterRequest request);

    /** Admin tao tai khoan: he thong sinh mat khau tam va gui toi email cua nguoi dung. */
    CreatedUserResponse createUser(CreateUserRequest request);

    UserResponse updateProfile(String userId, UpdateProfileRequest request);

    void changePassword(String userId, ChangePasswordRequest request);

    UserResponse updateUserStatus(String userId, boolean active, String currentUserId);

    void deleteUser(String userId, String currentUserId);
}
