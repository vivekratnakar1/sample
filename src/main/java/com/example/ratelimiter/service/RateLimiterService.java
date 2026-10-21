package com.example.ratelimiter.service;

import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import com.example.ratelimiter.config.RateLimitProperties;

@Service
public class RateLimiterService {

    private final StringRedisTemplate redisTemplate;
    private final RateLimitProperties properties;
    private final DefaultRedisScript<List> incrementScript;

    public RateLimiterService(
            StringRedisTemplate redisTemplate,
            RateLimitProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;

        // Lua runs atomically inside Redis, so concurrent requests cannot
        // race between incrementing the counter and setting its expiry.
        this.incrementScript = new DefaultRedisScript<>();
        this.incrementScript.setScriptText("""
                local count = redis.call('INCR', KEYS[1])
                if count == 1 then
                    redis.call('EXPIRE', KEYS[1], ARGV[1])
                end
                local ttl = redis.call('TTL', KEYS[1])
                return {count, ttl}
                """);
        this.incrementScript.setResultType(List.class);
    }

    public RateLimitResult checkLimit(String clientKey) {
        String redisKey = "rate-limit:" + clientKey;

        List<?> result = redisTemplate.execute(
                incrementScript,
                List.of(redisKey),
                String.valueOf(properties.getWindowSeconds()));

        if (result == null || result.size() < 2) {
            // Fail closed: do not silently allow traffic if Redis returns
            // an unexpected response. Production systems may choose a
            // different fail-open/fail-closed policy based on requirements.
            throw new IllegalStateException("Redis did not return a rate-limit result");
        }

        long count = ((Number) result.get(0)).longValue();
        long ttl = ((Number) result.get(1)).longValue();
        boolean allowed = count <= properties.getLimit();

        return new RateLimitResult(count, Math.max(ttl, 0), allowed);
    }

    public int getLimit() {
        return properties.getLimit();
    }
}
