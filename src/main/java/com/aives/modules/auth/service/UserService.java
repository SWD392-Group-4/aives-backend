package com.aives.modules.auth.service;

import com.aives.modules.auth.dto.ChangePasswordRequest;
import com.aives.modules.auth.dto.CreateUserRequest;
import com.aives.modules.auth.dto.UpdateProfileRequest;
import com.aives.modules.auth.dto.UserResponse;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface UserService {

    List<UserResponse> getAllUsers();

    UserResponse getUserById(String userId);

    /** Tra ve map id -> ho ten. Id khong ton tai thi khong co trong map. Dung cho module khac hien ten user. */
    Map<String, String> getFullNamesByIds(Collection<String> userIds);

    UserResponse createUser(CreateUserRequest request);

    UserResponse updateProfile(String userId, UpdateProfileRequest request);

    void changePassword(String userId, ChangePasswordRequest request);

    UserResponse updateUserStatus(String userId, boolean active, String currentUserId);

    void deleteUser(String userId, String currentUserId);
}
