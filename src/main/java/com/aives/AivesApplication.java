package com.aives;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@SpringBootApplication
public class AivesApplication {

    public static void main(String[] args) {
        loadDotEnv();
        SpringApplication.run(AivesApplication.class, args);
    }

    /**
     * Tu dong nap bien moi truong tu file .env neu ton tai
     */
    private static void loadDotEnv() {
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
                } catch (IOException e) {
                    System.err.println("Could not load .env from " + path + ": " + e.getMessage());
                }
                break;
            }
        }
    }
}
