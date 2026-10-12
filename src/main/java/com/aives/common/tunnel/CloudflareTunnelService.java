package com.aives.common.tunnel;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tự động khởi chạy Cloudflare Quick Tunnel khi Backend khởi động (Spring Boot ApplicationReadyEvent).
 * Bắt URL công khai ngẫu nhiên (https://*.trycloudflare.com) và tự động đồng bộ sang file .env của aives-mobile.
 */
@Service
public class CloudflareTunnelService {

    private static final Logger log = LoggerFactory.getLogger(CloudflareTunnelService.class);
    private static final Pattern TUNNEL_URL_PATTERN = Pattern.compile("https://[a-zA-Z0-9-]+\\.trycloudflare\\.com");

    @Value("${app.tunnel.cloudflare.enabled:true}")
    private boolean tunnelEnabled;

    @Value("${server.port:8080}")
    private int serverPort;

    private Process tunnelProcess;
    private volatile String publicTunnelUrl;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!tunnelEnabled) {
            log.info("[CloudflareTunnel] Tính năng Tunnel đang bị tắt (app.tunnel.cloudflare.enabled=false).");
            return;
        }

        Thread tunnelThread = new Thread(this::startTunnel, "cf-tunnel-starter");
        tunnelThread.setDaemon(true);
        tunnelThread.start();
    }

    private void startTunnel() {
        String executable = resolveCloudflaredExecutable();
        if (executable == null) {
            printInstallGuide();
            return;
        }

        try {
            log.info("[CloudflareTunnel] Đang khởi chạy Cloudflare Tunnel cho port {}...", serverPort);
            List<String> command = List.of(
                    executable,
                    "tunnel",
                    "--url", "http://localhost:" + serverPort,
                    "--no-autoupdate"
            );

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            tunnelProcess = processBuilder.start();

            // Đăng ký hook tắt tiến trình khi JVM thoát
            Runtime.getRuntime().addShutdownHook(new Thread(this::stopTunnel));

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(tunnelProcess.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                boolean urlDiscovered = false;
                while ((line = reader.readLine()) != null) {
                    if (!urlDiscovered) {
                        Matcher matcher = TUNNEL_URL_PATTERN.matcher(line);
                        if (matcher.find()) {
                            urlDiscovered = true;
                            publicTunnelUrl = matcher.group();
                            onTunnelUrlDiscovered(publicTunnelUrl);
                        }
                    }
                }
            }

            int exitCode = tunnelProcess.waitFor();
            log.info("[CloudflareTunnel] Tiến trình Tunnel đã dừng (exit code: {}).", exitCode);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("[CloudflareTunnel] Lỗi khi chạy Cloudflare Tunnel: {}", e.getMessage());
        }
    }

    private void onTunnelUrlDiscovered(String tunnelUrl) {
        String apiUrl = tunnelUrl + "/api";
        String swaggerUrl = tunnelUrl + "/swagger-ui.html";

        printSuccessBanner(tunnelUrl, apiUrl, swaggerUrl);
        syncToMobileEnv(apiUrl);
        writeSharedEndpointFile(tunnelUrl, apiUrl, swaggerUrl);
    }

    private void printSuccessBanner(String tunnelUrl, String apiUrl, String swaggerUrl) {
        System.out.println("\n" + "=".repeat(85));
        System.out.println("🚀 [CLOUDFLARE TUNNEL] KẾT NỐI BACKEND THÀNH CÔNG VỚI INTERNET!");
        System.out.println("   🌐 URL Công khai:  " + tunnelUrl);
        System.out.println("   🔌 API Base URL:   " + apiUrl);
        System.out.println("   📖 Swagger UI:     " + swaggerUrl);
        System.out.println("   ⚡ Mobile App sẽ tự động nhận URL này mà không cần cấu hình lại thủ công!");
        System.out.println("=".repeat(85) + "\n");
    }

    /**
     * Tự động cập nhật EXPO_PUBLIC_API_URL vào aives-mobile/.env
     */
    private void syncToMobileEnv(String apiUrl) {
        Path[] possibleMobilePaths = new Path[]{
                Path.of("../aives-mobile"),
                Path.of("aives-mobile"),
                Path.of("../../aives-mobile")
        };

        Path mobileDir = null;
        for (Path path : possibleMobilePaths) {
            if (Files.isDirectory(path)) {
                mobileDir = path;
                break;
            }
        }

        if (mobileDir == null) {
            log.debug("[CloudflareTunnel] Không tìm thấy thư mục aives-mobile để đồng bộ .env.");
            return;
        }

        Path envFile = mobileDir.resolve(".env");
        Path envExampleFile = mobileDir.resolve(".env.example");

        try {
            if (Files.notExists(envFile) && Files.exists(envExampleFile)) {
                Files.copy(envExampleFile, envFile, StandardCopyOption.REPLACE_EXISTING);
                log.info("[CloudflareTunnel] Đã tự động tạo file {} từ .env.example", envFile);
            }

            List<String> lines = Files.exists(envFile) ? Files.readAllLines(envFile, StandardCharsets.UTF_8) : new ArrayList<>();
            List<String> newLines = new ArrayList<>();
            boolean replaced = false;

            for (String line : lines) {
                if (line.trim().startsWith("EXPO_PUBLIC_API_URL=")) {
                    newLines.add("EXPO_PUBLIC_API_URL=" + apiUrl);
                    replaced = true;
                } else {
                    newLines.add(line);
                }
            }

            if (!replaced) {
                newLines.add("EXPO_PUBLIC_API_URL=" + apiUrl);
            }

            Files.write(envFile, newLines, StandardCharsets.UTF_8);
            System.out.println("✅ [CloudflareTunnel] Đã tự động cập nhật " + envFile + " -> EXPO_PUBLIC_API_URL=" + apiUrl);
        } catch (Exception e) {
            log.warn("[CloudflareTunnel] Không thể ghi file .env của aives-mobile: {}", e.getMessage());
        }
    }

    /**
     * Lưu thông tin endpoint vào file JSON chung ở root để mọi công cụ/script có thể đọc được.
     */
    private void writeSharedEndpointFile(String tunnelUrl, String apiUrl, String swaggerUrl) {
        Path[] candidateRoots = new Path[]{
                Path.of(".."),
                Path.of(".")
        };

        for (Path root : candidateRoots) {
            if (Files.exists(root.resolve("aives-mobile")) || Files.exists(root.resolve("aives-backend"))) {
                Path endpointFile = root.resolve("tunnel-endpoints.json");
                try {
                    String json = String.format("""
                            {
                              "backend": {
                                "tunnelUrl": "%s",
                                "apiUrl": "%s",
                                "swaggerUrl": "%s",
                                "localPort": %d,
                                "updatedAt": "%s"
                              }
                            }
                            """,
                            escapeJson(tunnelUrl),
                            escapeJson(apiUrl),
                            escapeJson(swaggerUrl),
                            serverPort,
                            Instant.now().toString());
                    Files.writeString(endpointFile, json, StandardCharsets.UTF_8);
                    log.info("[CloudflareTunnel] Đã ghi thông tin endpoint vào {}", endpointFile.toAbsolutePath().normalize());
                } catch (Exception e) {
                    log.debug("[CloudflareTunnel] Không thể ghi file {}: {}", endpointFile, e.getMessage());
                }
                break;
            }
        }
    }

    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * Tìm đường dẫn file thực thi cloudflared trên máy
     */
    private String resolveCloudflaredExecutable() {
        // 1. Kiểm tra lệnh trong PATH
        if (canExecute("cloudflared")) {
            return "cloudflared";
        }

        // 2. Kiểm tra các đường dẫn mặc định trên Windows
        String localAppData = System.getenv("LOCALAPPDATA");
        String userProfile = System.getenv("USERPROFILE");
        String programFilesX86 = System.getenv("ProgramFiles(x86)");
        String programFiles = System.getenv("ProgramFiles");

        List<String> candidatePaths = new ArrayList<>();
        if (programFilesX86 != null) {
            candidatePaths.add(programFilesX86 + "\\cloudflared\\cloudflared.exe");
        }
        if (programFiles != null) {
            candidatePaths.add(programFiles + "\\cloudflared\\cloudflared.exe");
        }
        candidatePaths.add("C:\\Program Files (x86)\\cloudflared\\cloudflared.exe");
        candidatePaths.add("C:\\Program Files\\cloudflared\\cloudflared.exe");
        if (localAppData != null) {
            candidatePaths.add(localAppData + "\\Programs\\cloudflared\\cloudflared.exe");
            candidatePaths.add(localAppData + "\\Microsoft\\WinGet\\Links\\cloudflared.exe");
        }
        if (userProfile != null) {
            candidatePaths.add(userProfile + "\\bin\\cloudflared.exe");
        }
        candidatePaths.add("tools\\cloudflared.exe");

        for (String pathStr : candidatePaths) {
            File f = new File(pathStr);
            if (f.isFile() && f.canExecute()) {
                return f.getAbsolutePath();
            }
        }

        return null;
    }

    private boolean canExecute(String command) {
        try {
            Process p = new ProcessBuilder(command, "--version").start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void printInstallGuide() {
        System.err.println("\n" + "=".repeat(85));
        System.err.println("⚠️ [CLOUDFLARE TUNNEL] KHÔNG TÌM THẤY LỆNH 'cloudflared' TRÊN MÁY TÍNH!");
        System.err.println("👉 Để bật tính năng tạo URL công khai tự động cho Mobile, vui lòng cài đặt cloudflared:");
        System.err.println("   Cách 1 (Khuyên dùng - Windows):");
        System.err.println("          winget install --id Cloudflare.cloudflared");
        System.err.println("   Cách 2: Tải file cloudflared-windows-amd64.exe từ GitHub Cloudflare đổi tên thành cloudflared.exe");
        System.err.println("           và đặt vào PATH hoặc C:\\Program Files (x86)\\cloudflared\\");
        System.err.println("   (Backend vẫn hoạt động bình thường tại http://localhost:" + serverPort + ")");
        System.err.println("=".repeat(85) + "\n");
    }

    @PreDestroy
    public void stopTunnel() {
        if (tunnelProcess != null && tunnelProcess.isAlive()) {
            try {
                log.info("[CloudflareTunnel] Đang dừng tiến trình Cloudflare Tunnel...");
                tunnelProcess.descendants().forEach(ProcessHandle::destroyForcibly);
                tunnelProcess.destroyForcibly();
            } catch (Exception e) {
                log.debug("[CloudflareTunnel] Lỗi khi dừng tunnel: {}", e.getMessage());
            }
        }
    }

    public String getPublicTunnelUrl() {
        return publicTunnelUrl;
    }
}
