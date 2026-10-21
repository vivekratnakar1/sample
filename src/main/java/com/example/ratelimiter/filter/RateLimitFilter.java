package com.example.ratelimiter.filter;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.ratelimiter.config.RateLimitProperties;
import com.example.ratelimiter.service.RateLimitResult;
import com.example.ratelimiter.service.RateLimiterService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiterService rateLimiterService;
    private final RateLimitProperties properties;

    public RateLimitFilter(
            RateLimiterService rateLimiterService,
            RateLimitProperties properties) {
        this.rateLimiterService = rateLimiterService;
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only apply the limiter to the configured API path prefix.
        return !request.getRequestURI().startsWith(properties.getPathPrefix());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        // Demo identity: the remote IP is used as the client key.
        // Behind a proxy/load balancer, configure trusted proxy handling
        // before relying on forwarded headers such as X-Forwarded-For.
        String clientKey = request.getRemoteAddr();

        RateLimitResult result = rateLimiterService.checkLimit(clientKey);

        long remaining = Math.max(
                rateLimiterService.getLimit() - result.requestCount(), 0);

        response.setHeader("X-RateLimit-Limit",
                String.valueOf(rateLimiterService.getLimit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
        response.setHeader("X-RateLimit-Reset", String.valueOf(result.ttlSeconds()));

        if (!result.allowed()) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Retry-After", String.valueOf(result.ttlSeconds()));

            response.getWriter().write("""
                    {
                      "status": 429,
                      "error": "Too Many Requests",
                      "message": "Rate limit exceeded. Please retry after the window resets."
                    }
                    """);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
