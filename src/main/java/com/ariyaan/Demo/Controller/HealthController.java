package com.ariyaan.Demo.Controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    private final org.springframework.jdbc.core.JdbcTemplate db;
    public HealthController(org.springframework.jdbc.core.JdbcTemplate db) { this.db=db; }

    @GetMapping("/health")
    public org.springframework.http.ResponseEntity<Map<String, String>> health() {
        try {
            db.queryForObject("SELECT 1", Integer.class);
            return org.springframework.http.ResponseEntity.ok(Map.of("status", "UP"));
        } catch (org.springframework.dao.DataAccessException ex) {
            return org.springframework.http.ResponseEntity.status(503).body(Map.of("status", "DOWN"));
        }
    }
    @GetMapping("/health/live")
    public Map<String,String> live() { return Map.of("status","UP"); }
}
