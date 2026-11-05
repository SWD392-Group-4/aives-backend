package com.aives.common.config;

import com.aives.modules.auth.entity.Role;
import com.aives.modules.auth.entity.User;
import com.aives.modules.auth.enums.RoleName;
import com.aives.modules.auth.repository.RoleRepository;
import com.aives.modules.auth.repository.UserRepository;
import com.aives.modules.auth.util.UserIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Chay moi lan khoi dong:
 *   1. Bang roles thieu role nao (ADMIN / LECTURER / STUDENT) thi tao role do.
 *   2. Bang users con trong thi tao du lieu mau (1 admin, 2 lecturer, 7 student).
 *      Da co du lieu thi bo qua.
 *   Khong seed mon hoc / chuong / rubric: admin tu tao o man "Chuong trinh hoc".
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserIdGenerator userIdGenerator;

    @Value("${app.seed.admin.email}")
    private String adminEmail;

    @Value("${app.seed.admin.password}")
    private String adminPassword;

    @Value("${app.seed.admin.full-name:System Admin}")
    private String adminFullName;

    @Value("${app.seed.default-password}")
    private String defaultPassword;

    @Override
    @Transactional
    public void run(String... args) {
        seedRoles();
        seedUsers();
    }

    private void seedUsers() {
        if (userRepository.count() > 0) {
            return;
        }

        // 1 admin
        createUser(adminEmail.trim().toLowerCase(), adminPassword, adminFullName, RoleName.ADMIN);

        // 2 lecturer
        createUser("lecturer01@fe.edu.vn", defaultPassword, "Nguyễn Thị Hồng Lan", RoleName.LECTURER);
        createUser("lecturer02@fe.edu.vn", defaultPassword, "Trần Quốc Bảo", RoleName.LECTURER);

        // 7 student
        createUser("student01@fpt.edu.vn", defaultPassword, "Lê Minh Anh", RoleName.STUDENT);
        createUser("student02@fpt.edu.vn", defaultPassword, "Phạm Gia Huy", RoleName.STUDENT);
        createUser("student03@fpt.edu.vn", defaultPassword, "Võ Thị Thu Trang", RoleName.STUDENT);
        createUser("student04@fpt.edu.vn", defaultPassword, "Đặng Hoàng Nam", RoleName.STUDENT);
        createUser("student05@fpt.edu.vn", defaultPassword, "Bùi Khánh Linh", RoleName.STUDENT);
        createUser("student06@fpt.edu.vn", defaultPassword, "Hoàng Đức Thịnh", RoleName.STUDENT);
        createUser("student07@fpt.edu.vn", defaultPassword, "Ngô Bảo Ngọc", RoleName.STUDENT);

        log.info("Seeded {} sample users", userRepository.count());
    }

    /** roles_id = 2 chu cai tien to cua users_id (AD / LE / ST). */
    private void seedRoles() {
        for (RoleName name : RoleName.values()) {
            if (roleRepository.findByName(name).isEmpty()) {
                Role role = Role.builder()
                        .name(name)
                        .description(name.getDescription())
                        .build();
                role.setId(name.getIdPrefix());
                roleRepository.save(role);
            }
        }
    }

    private void createUser(String email, String rawPassword, String fullName, RoleName roleName) {
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException("Thiếu role " + roleName + " trong bảng roles"));
        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .fullName(fullName)
                .role(role)
                .active(true)
                .build();
        user.setId(userIdGenerator.generate(roleName));
        userRepository.save(user);
    }
}
