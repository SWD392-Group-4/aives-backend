package com.aives.modules.auth.service;

import com.aives.modules.auth.dto.CreateUserRequest;
import com.aives.modules.auth.dto.UserResponse;
import java.util.List;

public interface UserService {

    List<UserResponse> getAllUsers();

    UserResponse createUser(CreateUserRequest request);

    void deleteUser(Long userId, Long currentUserId);
}
