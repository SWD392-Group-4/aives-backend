package com.aives.modules.auth.service.impl;

import com.aives.common.exception.BusinessException;
import com.aives.common.exception.ErrorCode;
import com.aives.modules.auth.dto.ChangePasswordRequest;
import com.aives.modules.auth.dto.CreateUserRequest;
import com.aives.modules.auth.dto.UpdateProfileRequest;
import com.aives.modules.auth.dto.UserResponse;
import com.aives.modules.auth.entity.User;
import com.aives.modules.auth.enums.Role;
import com.aives.modules.auth.mapper.UserMapper;
import com.aives.modules.auth.repository.UserRepository;
import com.aives.modules.auth.service.UserService;
import com.aives.modules.auth.util.UserIdGenerator;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final UserIdGenerator userIdGenerator;

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll(Sort.by(Sort.Direction.ASC, "createdAt"))
                .stream()
                .map(userMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(String userId) {
        return userMapper.toResponse(findUser(userId));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> getFullNamesByIds(Collection<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));
    }

    @Override
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        Role role = request.getRole();

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName().trim())
                .role(role)
                .active(true)
                .build();
        user.setId(userIdGenerator.generate(role));

        return userMapper.toResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserResponse updateProfile(String userId, UpdateProfileRequest request) {
        User user = findUser(userId);
        user.setFullName(request.getFullName().trim());
        return userMapper.toResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public void changePassword(String userId, ChangePasswordRequest request) {
        User user = findUser(userId);
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.WRONG_CURRENT_PASSWORD);
        }
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    @Override
    @Transactional
    public UserResponse updateUserStatus(String userId, boolean active, String currentUserId) {
        // Admin khong duoc tu khoa chinh minh, tranh truong hop khong con ai vao duoc he thong
        if (!active && userId.equals(currentUserId)) {
            throw new BusinessException(ErrorCode.CANNOT_DISABLE_SELF);
        }
        User user = findUser(userId);
        user.setActive(active);
        return userMapper.toResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public void deleteUser(String userId, String currentUserId) {
        if (userId.equals(currentUserId)) {
            throw new BusinessException(ErrorCode.CANNOT_DELETE_SELF);
        }
        userRepository.delete(findUser(userId));
    }

    private User findUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
