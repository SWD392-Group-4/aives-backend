package com.aives.modules.auth.service.impl;

import com.aives.common.dto.PageResponse;
import com.aives.common.exception.BusinessException;
import com.aives.common.exception.ErrorCode;
import com.aives.modules.auth.dto.ChangePasswordRequest;
import com.aives.modules.auth.dto.CreateUserRequest;
import com.aives.modules.auth.dto.CreatedUserResponse;
import com.aives.modules.auth.dto.RegisterRequest;
import com.aives.modules.auth.dto.UpdateProfileRequest;
import com.aives.modules.auth.dto.UserResponse;
import com.aives.modules.auth.entity.Role;
import com.aives.modules.auth.entity.User;
import com.aives.modules.auth.enums.OtpType;
import com.aives.modules.auth.enums.RoleName;
import com.aives.modules.auth.mapper.UserMapper;
import com.aives.modules.auth.repository.RoleRepository;
import com.aives.modules.auth.repository.UserRepository;
import com.aives.common.security.UserPrincipal;
import com.aives.modules.auth.service.AccountMailService;
import com.aives.modules.auth.service.OtpService;
import com.aives.modules.auth.service.UserService;
import com.aives.modules.auth.util.TempPasswordGenerator;
import com.aives.modules.auth.util.UserIdGenerator;
import com.aives.modules.system.service.AuditService;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final UserIdGenerator userIdGenerator;
    private final TempPasswordGenerator tempPasswordGenerator;
    private final AccountMailService accountMailService;
    private final OtpService otpService;
    private final AuditService auditService;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> getUsers(RoleName role, String keyword, int page, int size) {
        Specification<User> spec = (root, query, cb) -> cb.conjunction();
        if (role != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("role").get("name"), role));
        }
        if (StringUtils.hasText(keyword)) {
            String kw = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("email")), kw),
                    cb.like(cb.lower(root.get("fullName")), kw),
                    cb.like(cb.lower(root.get("id")), kw)
            ));
        }

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<User> result = userRepository.findAll(spec, pageable);
        List<UserResponse> items = result.getContent().stream()
                .map(userMapper::toResponse)
                .toList();

        return new PageResponse<>(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

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
    @Transactional(readOnly = true)
    public Map<String, UserResponse> getUsersByIds(Collection<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, userMapper::toResponse));
    }

    @Override
    @Transactional
    public UserResponse registerStudent(RegisterRequest request) {
        otpService.verifyAndConsumeOtp(request.getEmail(), request.getOtp(), OtpType.REGISTRATION);
        User user = newUser(request.getEmail(), request.getFullName(), RoleName.STUDENT, request.getPassword(), false);
        return userMapper.toResponse(userRepository.save(user));
    }

    /**
     * Mat khau tam chi ton tai trong email gui cho nguoi dung: DB chi luu ban ma hoa,
     * API khong tra ve. Gui mail that bai thi nem loi -> transaction rollback, tai khoan khong duoc tao
     * (tranh co tai khoan ma khong ai biet mat khau).
     */
    @Override
    @Transactional
    public CreatedUserResponse createUser(CreateUserRequest request) {
        String temporaryPassword = tempPasswordGenerator.generate();
        User user = newUser(request.getEmail(), request.getFullName(), request.getRole(), temporaryPassword, true);
        User saved = userRepository.saveAndFlush(user);

        boolean emailSent = accountMailService.sendAccountCreated(saved, temporaryPassword);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String actorId = auth != null && auth.getPrincipal() instanceof UserPrincipal p ? p.getId() : "SYSTEM";
        auditService.log(
                actorId,
                "CREATE_USER",
                "USER",
                saved.getId(),
                null,
                String.format("{\"id\":\"%s\",\"email\":\"%s\",\"fullName\":\"%s\",\"role\":\"%s\"}",
                        saved.getId(), saved.getEmail(), saved.getFullName(), saved.getRole().getName().name())
        );

        return new CreatedUserResponse(userMapper.toResponse(saved), emailSent);
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
        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.NEW_PASSWORD_SAME_AS_CURRENT);
        }
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        // Da tu dat mat khau rieng thi khong con bi bat doi nua
        user.setMustChangePassword(false);
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
        boolean oldActive = user.isActive();
        user.setActive(active);
        User saved = userRepository.save(user);

        auditService.log(
                currentUserId != null ? currentUserId : "SYSTEM",
                "UPDATE_STATUS",
                "USER",
                userId,
                oldActive ? "ACTIVE" : "INACTIVE",
                active ? "ACTIVE" : "INACTIVE"
        );

        return userMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void deleteUser(String userId, String currentUserId) {
        if (userId.equals(currentUserId)) {
            throw new BusinessException(ErrorCode.CANNOT_DELETE_SELF);
        }
        User user = findUser(userId);
        String oldUserData = String.format("{\"id\":\"%s\",\"email\":\"%s\",\"fullName\":\"%s\",\"role\":\"%s\",\"active\":%b}",
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole() != null ? user.getRole().getName().name() : "",
                user.isActive());
        userRepository.delete(user);

        auditService.log(
                currentUserId != null ? currentUserId : "SYSTEM",
                "DELETE_USER",
                "USER",
                userId,
                oldUserData,
                null
        );
    }

    private User newUser(String rawEmail, String fullName, RoleName roleName, String rawPassword, boolean mustChangePassword) {
        String email = rawEmail.trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR,
                        "Thiếu role " + roleName + " trong bảng roles"));

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .fullName(fullName.trim())
                .role(role)
                .active(true)
                .mustChangePassword(mustChangePassword)
                .build();
        user.setId(userIdGenerator.generate(roleName));
        return user;
    }

    private User findUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
