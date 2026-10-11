package com.aives;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@SpringBootApplication
public class AivesApplication {

    private static final String[] REQUIRED_ENV_VARS = {
            "SPRING_PROFILES_ACTIVE",
            "DB_URL",
            "DB_USERNAME",
            "DB_PASSWORD",
            "JWT_SECRET",
            "CORS_ALLOWED_ORIGINS",
            "ADMIN_EMAIL",
            "ADMIN_PASSWORD",
            "SEED_DEFAULT_PASSWORD"
    };

    public static void main(String[] args) {
        boolean envLoaded = loadDotEnv();
        validateEnvironment(envLoaded);
        SpringApplication.run(AivesApplication.class, args);
    }

    /**
     * Tự động nạp biến môi trường từ file .env nếu tồn tại
     */
    private static boolean loadDotEnv() {
        Path[] possiblePaths = new Path[]{
                Path.of(".env"),
                Path.of("../.env")
        };

        for (Path path : possiblePaths) {
            if (Files.exists(path)) {
                try {
                    List<String> lines = Files.readAllLines(path);
                    for (String line : lines) {
                        line = line.trim();
                        if (!line.isEmpty() && !line.startsWith("#") && line.contains("=")) {
                            int idx = line.indexOf('=');
                            String key = line.substring(0, idx).trim();
                            String value = line.substring(idx + 1).trim();
                            if (System.getProperty(key) == null && System.getenv(key) == null) {
                                System.setProperty(key, value);
                            }
                        }
                    }
                    return true;
                } catch (IOException e) {
                    System.err.println("Could not load .env from " + path + ": " + e.getMessage());
                }
            }
        }
        return false;
    }

    /**
     * Báo lỗi rõ ràng và dừng chương trình ngay lập tức nếu thiếu .env hoặc thiếu biến quan trọng
     */
    private static void validateEnvironment(boolean envFileLoaded) {
        if (!envFileLoaded && Files.notExists(Path.of(".env"))) {
            System.err.println("\n" + "=".repeat(75));
            System.err.println("❌ LỖI KHỞI ĐỘNG: KHÔNG TÌM THẤY FILE .env TRONG aives-backend!");
            System.err.println("👉 Vui lòng copy file .env.example thành .env và điền các thông tin bí mật.");
            System.err.println("   Lệnh tạo nhanh: cp .env.example .env");
            System.err.println("=".repeat(75) + "\n");
        }

        List<String> missingVars = new ArrayList<>();
        for (String varName : REQUIRED_ENV_VARS) {
            String val = System.getProperty(varName);
            if (val == null || val.isBlank()) {
                val = System.getenv(varName);
            }
            if (val == null || val.isBlank() || val.contains("your_") || val.contains("<chua_dien>")) {
                missingVars.add(varName);
            }
        }

        if (!missingVars.isEmpty()) {
            System.err.println("\n" + "=".repeat(75));
            System.err.println("❌ LỖI CẤU HÌNH BIẾN MÔI TRƯỜNG TRONG .env:");
            for (String missing : missingVars) {
                System.err.println("   - Thiếu hoặc chưa cấu hình biến: " + missing);
            }
            System.err.println("\n👉 Xem mẫu các biến tại file aives-backend/.env.example");
            System.err.println("=".repeat(75) + "\n");
            System.exit(1);
        }
    }
}
