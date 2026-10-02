package com.aives.common.config;

import com.aives.modules.auth.entity.User;
import com.aives.modules.auth.enums.Role;
import com.aives.modules.auth.repository.UserRepository;
import com.aives.modules.auth.util.UserIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Chay 1 lan: khi bang users con trong thi tao du lieu mau
 * (1 admin, 2 lecturer, 7 student). Lan chay sau da co du lieu thi bo qua.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
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
        if (userRepository.count() > 0) {
            return;
        }

        // 1 admin
        createUser(adminEmail.trim().toLowerCase(), adminPassword, adminFullName, Role.ADMIN, null);

        // 2 lecturer
        createUser("lecturer01@fe.edu.vn", defaultPassword, "Nguyễn Thị Hồng Lan", Role.LECTURER, null);
        createUser("lecturer02@fe.edu.vn", defaultPassword, "Trần Quốc Bảo", Role.LECTURER, null);

        // 7 student
        createUser("student01@fpt.edu.vn", defaultPassword, "Lê Minh Anh", Role.STUDENT, "SE180001");
        createUser("student02@fpt.edu.vn", defaultPassword, "Phạm Gia Huy", Role.STUDENT, "SE180002");
        createUser("student03@fpt.edu.vn", defaultPassword, "Võ Thị Thu Trang", Role.STUDENT, "SE180003");
        createUser("student04@fpt.edu.vn", defaultPassword, "Đặng Hoàng Nam", Role.STUDENT, "SE180004");
        createUser("student05@fpt.edu.vn", defaultPassword, "Bùi Khánh Linh", Role.STUDENT, "SE180005");
        createUser("student06@fpt.edu.vn", defaultPassword, "Hoàng Đức Thịnh", Role.STUDENT, "SE180006");
        createUser("student07@fpt.edu.vn", defaultPassword, "Ngô Bảo Ngọc", Role.STUDENT, "SE180007");

        log.info("Seeded {} sample users", userRepository.count());
    }

    private void createUser(String email, String rawPassword, String fullName, Role role, String studentCode) {
        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .fullName(fullName)
                .role(role)
                .studentCode(studentCode)
                .active(true)
                .build();
        user.setId(userIdGenerator.generate(role));
        userRepository.save(user);
    }
}
