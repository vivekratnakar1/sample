package com.example.ratelimiter.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimiterConfig {
    // Registers app.rate-limit.* properties as a Spring-managed bean.
}
