package com.aives.common.config;

import com.aives.modules.auth.entity.Role;
import com.aives.modules.auth.entity.User;
import com.aives.modules.auth.enums.RoleName;
import com.aives.modules.auth.repository.RoleRepository;
import com.aives.modules.auth.repository.UserRepository;
import com.aives.modules.auth.util.UserIdGenerator;
import com.aives.modules.content.entity.Chapter;
import com.aives.modules.content.entity.Course;
import com.aives.modules.content.entity.Rubric;
import com.aives.modules.content.entity.RubricCriterion;
import com.aives.modules.content.repository.ChapterRepository;
import com.aives.modules.content.repository.CourseRepository;
import com.aives.modules.content.repository.RubricCriterionRepository;
import com.aives.modules.content.repository.RubricRepository;
import java.math.BigDecimal;
import java.util.List;
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
    private final CourseRepository courseRepository;
    private final ChapterRepository chapterRepository;
    private final RubricRepository rubricRepository;
    private final RubricCriterionRepository rubricCriterionRepository;

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
        seedDefaultCourses();
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

    private void seedDefaultCourses() {
        if (courseRepository.count() > 0) {
            return;
        }

        Course course = Course.builder()
                .courseCode("SWD392")
                .name("Kiến trúc & Thiết kế Phần mềm (SWD392)")
                .description("Môn học chuyên sâu về Clean Architecture, Microservices, Design Patterns và thực hành thiết kế hệ thống.")
                .build();
        course.setId("SWD392");
        Course savedCourse = courseRepository.save(course);

        Chapter chap1 = Chapter.builder()
                .course(savedCourse)
                .name("Chương 1: Clean Architecture & SOLID Principles")
                .orderNo(1)
                .description("Nguyên lý SOLID, Clean Architecture, Dependency Inversion và phân tách tầng ứng dụng.")
                .build();
        chap1.setId("CHAP_01");
        chapterRepository.save(chap1);

        Chapter chap2 = Chapter.builder()
                .course(savedCourse)
                .name("Chương 2: Microservices & Event-Driven Architecture")
                .orderNo(2)
                .description("Kiến trúc Microservices, API Gateway, Message Broker và quản trị phân tán.")
                .build();
        chap2.setId("CHAP_02");
        chapterRepository.save(chap2);

        // Khởi tạo Rubric môn học đạt chuẩn 100% (BR-BANK-001)
        Rubric rubric = Rubric.builder()
                .course(savedCourse)
                .name("Rubric Đánh Giá Vấn Đáp Môn SWD392")
                .description("Thang điểm chuẩn mực duy nhất đánh giá năng lực vấn đáp kiến trúc phần mềm môn SWD392")
                .createdBy("SYSTEM")
                .isLocked(false)
                .maxScore(new BigDecimal("10.00"))
                .build();
        rubric.setId("RUB_SWD392");
        Rubric savedRubric = rubricRepository.save(rubric);

        RubricCriterion c1 = RubricCriterion.builder()
                .rubric(savedRubric)
                .name("Khái niệm cốt lõi & Nguyên lý thiết kế")
                .description("Nắm vững định nghĩa, nguyên lý SOLID, ưu/nhược điểm các mẫu kiến trúc")
                .weightPercent(new BigDecimal("40.00"))
                .orderNo(1)
                .build();
        c1.setId("CRT_SWD_01");

        RubricCriterion c2 = RubricCriterion.builder()
                .rubric(savedRubric)
                .name("Khả năng vận dụng & Kiến trúc thực tế")
                .description("Áp dụng đúng mô hình kiến trúc vào bài toán cụ thể, xử lý trade-off")
                .weightPercent(new BigDecimal("35.00"))
                .orderNo(2)
                .build();
        c2.setId("CRT_SWD_02");

        RubricCriterion c3 = RubricCriterion.builder()
                .rubric(savedRubric)
                .name("Kỹ năng giải trình & Thuật ngữ chuyên môn")
                .description("Trình bày mạch lạc, sử dụng chính xác thuật ngữ kỹ thuật, phản biện tự tin")
                .weightPercent(new BigDecimal("25.00"))
                .orderNo(3)
                .build();
        c3.setId("CRT_SWD_03");

        rubricCriterionRepository.saveAll(List.of(c1, c2, c3));
        log.info("Seeded default course SWD392, chapters, and 100% Rubric");
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
