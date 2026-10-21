package com.example.ratelimiter.service;

public record RateLimitResult(long requestCount, long ttlSeconds, boolean allowed) {
}
