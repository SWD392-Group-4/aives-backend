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
        boolean isDeploy = isDeployEnvironment();
        // Luôn thử nạp .env (bao gồm Render Secret Files: /etc/secrets/.env hoặc thư mục app)
        boolean envLoaded = loadDotEnv();
        validateEnvironment(envLoaded, isDeploy);
        SpringApplication.run(AivesApplication.class, args);
    }

    /**
     * Kiểm tra xem đang chạy ở môi trường deploy (Render/Cloud/Docker) hay local dev.
     * Khi APP_ENV=deploy/production hoặc RENDER=true, sẽ bỏ qua kiểm tra file .env cục bộ.
     */
    private static boolean isDeployEnvironment() {
        String env = getEnvOrProperty("APP_ENV");
        if ("deploy".equalsIgnoreCase(env) || "production".equalsIgnoreCase(env) || "prod".equalsIgnoreCase(env)) {
            return true;
        }
        return System.getenv("RENDER") != null || System.getenv("RENDER_SERVICE_ID") != null;
    }

    private static String getEnvOrProperty(String key) {
        String val = System.getProperty(key);
        if (val == null || val.isBlank()) {
            val = System.getenv(key);
        }
        return val != null ? val.trim() : null;
    }

    /**
     * Trích xuất danh sách key hợp lệ từ file cấu hình .env / .env.example
     */
    private static java.util.Set<String> extractEnvKeys(Path path) {
        java.util.Set<String> keys = new java.util.LinkedHashSet<>();
        if (!Files.exists(path)) {
            return keys;
        }
        try {
            List<String> lines = Files.readAllLines(path);
            for (String line : lines) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#") && line.contains("=")) {
                    int idx = line.indexOf('=');
                    String key = line.substring(0, idx).trim();
                    if (!key.isEmpty()) {
                        keys.add(key);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Không thể đọc keys từ " + path + ": " + e.getMessage());
        }
        return keys;
    }

    /**
     * Tự động nạp biến môi trường từ file .env nếu tồn tại
     * Hỗ trợ cả môi trường Local Dev lẫn Render Secret Files (/etc/secrets/.env)
     */
    private static boolean loadDotEnv() {
        List<Path> possiblePaths = new ArrayList<>();
        String customPath = System.getenv("ENV_FILE_PATH");
        if (customPath != null && !customPath.isBlank()) {
            possiblePaths.add(Path.of(customPath.trim()));
        }
        // Vị trí Secret Files tiêu chuẩn trên Render
        possiblePaths.add(Path.of("/etc/secrets/.env"));
        possiblePaths.add(Path.of("/etc/secrets/aives.env"));
        // Vị trí cục bộ
        possiblePaths.add(Path.of(".env"));
        possiblePaths.add(Path.of("../.env"));

        for (Path path : possiblePaths) {
            if (Files.isRegularFile(path) && Files.isReadable(path)) {
                try {
                    List<String> lines = Files.readAllLines(path);
                    for (String line : lines) {
                        line = line.trim();
                        if (!line.isEmpty() && !line.startsWith("#") && line.contains("=")) {
                            int idx = line.indexOf('=');
                            String key = line.substring(0, idx).trim();
                            String value = line.substring(idx + 1).trim();

                            // Bỏ dấu nháy kép hoặc đơn nếu có bọc quanh giá trị
                            if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
                                if (value.length() >= 2) {
                                    value = value.substring(1, value.length() - 1);
                                }
                            }

                            // Chỉ gán nếu biến môi trường hệ thống chưa có hoặc bị rỗng
                            String existingEnv = System.getenv(key);
                            if (System.getProperty(key) == null && (existingEnv == null || existingEnv.isBlank())) {
                                System.setProperty(key, value);
                            }
                        }
                    }
                    System.out.println("✅ [AIVES Backend] Đã nạp thành công biến môi trường từ file: " + path.toAbsolutePath());
                    return true;
                } catch (IOException e) {
                    System.err.println("Could not load .env from " + path + ": " + e.getMessage());
                }
            }
        }
        return false;
    }

    /**
     * Báo lỗi rõ ràng và dừng chương trình:
     * - Ở chế độ Deploy (Render): Kiểm tra nếu đã nạp file .env từ Secret Files hoặc dùng biến môi trường hệ thống.
     * - Ở chế độ Local Dev: Kiểm tra file .env và so sánh đồng bộ với .env.example.
     */
    private static void validateEnvironment(boolean envFileLoaded, boolean isDeploy) {
        if (isDeploy) {
            if (envFileLoaded) {
                System.out.println("\n🚀 [AIVES Backend] Đang chạy ở môi trường DEPLOY: Đã nạp cấu hình từ Secret File .env.");
            } else {
                System.out.println("\n🚀 [AIVES Backend] Đang chạy ở môi trường DEPLOY/PRODUCTION: Không phát hiện Secret File .env, sử dụng trực tiếp biến môi trường Render.");
            }
        } else {
            Path envPath = Path.of(".env");
            Path examplePath = Path.of(".env.example");

            if (!envFileLoaded && Files.notExists(envPath)) {
                System.err.println("\n" + "=".repeat(75));
                System.err.println("❌ LỖI KHỞI ĐỘNG: KHÔNG TÌM THẤY FILE .env TRONG aives-backend!");
                System.err.println("👉 Vui lòng copy file .env.example thành .env và điền các thông tin.");
                System.err.println("   Lệnh tạo nhanh: cp .env.example .env");
                System.err.println("=".repeat(75) + "\n");
                System.exit(1);
            }

            if (Files.exists(examplePath) && Files.exists(envPath)) {
                java.util.Set<String> exampleKeys = extractEnvKeys(examplePath);
                java.util.Set<String> envKeys = extractEnvKeys(envPath);

                List<String> missingInEnv = new ArrayList<>();
                for (String k : exampleKeys) {
                    if (!envKeys.contains(k)) {
                        missingInEnv.add(k);
                    }
                }

                if (!missingInEnv.isEmpty()) {
                    System.err.println("\n" + "=".repeat(75));
                    System.err.println("❌ LỖI ĐỒNG BỘ: FILE .env THIẾU BIẾN SO VỚI .env.example!");
                    System.err.println("👉 Đã phát hiện các biến mới trong .env.example mà .env của bạn chưa có:");
                    for (String missing : missingInEnv) {
                        System.err.println("   - " + missing);
                    }
                    System.err.println("\n👉 Vui lòng mở .env.example và cập nhật các biến trên vào .env của bạn!");
                    System.err.println("=".repeat(75) + "\n");
                    System.exit(1);
                }

                List<String> missingInExample = new ArrayList<>();
                for (String k : envKeys) {
                    if (!exampleKeys.contains(k)) {
                        missingInExample.add(k);
                    }
                }
                if (!missingInExample.isEmpty()) {
                    System.out.println("\n⚠️  CẢNH BÁO: Phát hiện biến mới trong .env chưa có trong .env.example:");
                    for (String k : missingInExample) {
                        System.out.println("   - " + k);
                    }
                    System.out.println("👉 Hãy nhớ cập nhật vào .env.example để chia sẻ cho các thành viên khác trong team!\n");
                }
            }
        }

        // Kiểm tra tính đầy đủ của các biến bắt buộc
        List<String> missingVars = new ArrayList<>();
        for (String varName : REQUIRED_ENV_VARS) {
            String val = getEnvOrProperty(varName);
            if (val == null || val.isBlank() || val.contains("your_") || val.contains("<chua_dien>")) {
                missingVars.add(varName);
            }
        }

        if (!missingVars.isEmpty()) {
            System.err.println("\n" + "=".repeat(75));
            System.err.println("❌ LỖI CẤU HÌNH: THIẾU BIẾN MÔI TRƯỜNG BẮT BUỘC:");
            for (String missing : missingVars) {
                System.err.println("   - " + missing);
            }
            if (isDeploy) {
                System.err.println("\n👉 Vui lòng cấu hình các biến trên tại Render Dashboard (Environment Variables).");
            } else {
                System.err.println("\n👉 Vui lòng cấu hình các biến trên tại file aives-backend/.env");
            }
            System.err.println("=".repeat(75) + "\n");
            System.exit(1);
        }
    }
}
