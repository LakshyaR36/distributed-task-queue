package com.taskqueue.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

/**
 * Basic rate limiting interceptor using Redis.
 * Limits to 100 requests per minute per IP.
 */
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate redisTemplate;
    
    private static final int MAX_REQUESTS_PER_MINUTE = 100;
    private static final String RATE_LIMIT_KEY_PREFIX = "dtq:ratelimit:";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String clientIp = request.getRemoteAddr();
        String key = RATE_LIMIT_KEY_PREFIX + clientIp;
        
        Long requests = redisTemplate.opsForValue().increment(key);
        if (requests != null && requests == 1) {
            // First request in the window, set expiration
            redisTemplate.expire(key, Duration.ofMinutes(1));
        }

        if (requests != null && requests > MAX_REQUESTS_PER_MINUTE) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.getWriter().write("Rate limit exceeded. Try again in a minute.");
            return false; // Reject request
        }

        return true; // Allow request
    }
}
