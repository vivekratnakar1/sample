package com.example.ratelimiter.controller;

import java.time.Instant;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApiController {

    @GetMapping("/api/hello")
    public Map<String, Object> hello() {
        // This method is reached only if the rate-limit filter allows the request.
        return Map.of(
                "message", "Hello! Your request was allowed.",
                "timestamp", Instant.now().toString());
    }

    @GetMapping("/api/products")
    public Map<String, Object> products() {
        // Sample endpoint to demonstrate that the same client limit is shared
        // across API endpoints covered by the /api/ path prefix.
        return Map.of(
                "products", new String[]{"Laptop", "Keyboard", "Mouse"},
                "count", 3);
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        // This endpoint is outside /api/ and is not rate-limited by this filter.
        return Map.of("status", "UP");
    }
}
