package com.aives.common.controller;

import com.aives.common.dto.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @GetMapping
    public ApiResponse<String> health() {
        return ApiResponse.ok("AIVES backend is running");
    }

    @GetMapping("/tables")
    public ApiResponse<java.util.List<String>> listTables() {
        java.util.List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name",
                String.class
        );
        return ApiResponse.ok(tables);
    }
}

