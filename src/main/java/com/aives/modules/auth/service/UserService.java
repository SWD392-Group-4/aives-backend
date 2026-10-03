package com.aives.modules.auth.service;

import com.aives.modules.auth.dto.ChangePasswordRequest;
import com.aives.modules.auth.dto.CreateUserRequest;
import com.aives.modules.auth.dto.UpdateProfileRequest;
import com.aives.modules.auth.dto.UserResponse;
import java.util.List;

public interface UserService {

    List<UserResponse> getAllUsers();

    UserResponse getUserById(String userId);

    UserResponse createUser(CreateUserRequest request);

    UserResponse updateProfile(String userId, UpdateProfileRequest request);

    void changePassword(String userId, ChangePasswordRequest request);

    UserResponse updateUserStatus(String userId, boolean active, String currentUserId);

    void deleteUser(String userId, String currentUserId);
}
