package com.java2nb.novel.common.aspect;

import com.java2nb.novel.common.annotation.LimitType;
import com.java2nb.novel.common.annotation.RateLimit;
import com.java2nb.novel.core.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Aspect
@Component
public class RateLimitAspect {

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    private final Map<String, SlidingWindowCounter> inMemoryCounters = new ConcurrentHashMap<>();

    @Pointcut("@annotation(rateLimit)")
    public void rateLimitPointcut(RateLimit rateLimit) {}

    @Around("rateLimitPointcut(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        String key = combineKey(joinPoint, rateLimit);
        int maxCount = rateLimit.count();
        int timeWindow = rateLimit.timeWindowSeconds();

        boolean allowed = checkAllowed(key, maxCount, timeWindow);
        if (!allowed) {
            log.warn("Rate limit exceeded for key: {}, maxCount: {}, timeWindow: {}s", key, maxCount, timeWindow);
            throw new BusinessException(rateLimit.message());
        }

        return joinPoint.proceed();
    }

    private boolean checkAllowed(String key, int maxCount, int timeWindowSeconds) {
        if (redisTemplate != null) {
            try {
                String redisKey = "rate_limit:" + key;
                Long currentCount = redisTemplate.opsForValue().increment(redisKey, 1);
                if (currentCount != null && currentCount == 1) {
                    redisTemplate.expire(redisKey, timeWindowSeconds, TimeUnit.SECONDS);
                }
                return currentCount != null && currentCount <= maxCount;
            } catch (Exception e) {
                log.warn("Redis rate limiter unavailable, falling back to in-memory window: {}", e.getMessage());
            }
        }

        // In-memory sliding window fallback
        SlidingWindowCounter counter = inMemoryCounters.computeIfAbsent(key, k -> new SlidingWindowCounter(timeWindowSeconds));
        return counter.allow(maxCount);
    }

    private String combineKey(ProceedingJoinPoint joinPoint, RateLimit rateLimit) {
        StringBuilder keySb = new StringBuilder();
        keySb.append(joinPoint.getSignature().getDeclaringTypeName())
             .append(".")
             .append(joinPoint.getSignature().getName());

        if (StringUtils.isNotBlank(rateLimit.key())) {
            keySb.append(":").append(rateLimit.key());
        }

        Object requestObj = getHttpServletRequest();
        if (rateLimit.limitType() == LimitType.IP) {
            keySb.append(":IP:").append(getClientIp(requestObj));
        } else if (rateLimit.limitType() == LimitType.USER) {
            Long userId = getAttributeLong(requestObj, "userId");
            keySb.append(":USER:").append(userId != null ? userId : getClientIp(requestObj));
        } else {
            keySb.append(":GLOBAL");
        }

        return keySb.toString();
    }

    private Object getHttpServletRequest() {
        try {
            RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                Method getRequest = attributes.getClass().getMethod("getRequest");
                return getRequest.invoke(attributes);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String getClientIp(Object request) {
        if (request == null) return "127.0.0.1";
        String ip = getHeader(request, "X-Forwarded-For");
        if (StringUtils.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = getHeader(request, "Proxy-Client-IP");
        }
        if (StringUtils.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            try {
                Method getRemoteAddr = request.getClass().getMethod("getRemoteAddr");
                ip = (String) getRemoteAddr.invoke(request);
            } catch (Exception ignored) {}
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return StringUtils.defaultIfBlank(ip, "127.0.0.1");
    }

    private String getHeader(Object request, String headerName) {
        if (request == null) return null;
        try {
            Method getHeaderMethod = request.getClass().getMethod("getHeader", String.class);
            return (String) getHeaderMethod.invoke(request, headerName);
        } catch (Exception e) {
            return null;
        }
    }

    private Long getAttributeLong(Object request, String attributeName) {
        if (request == null) return null;
        try {
            Method getAttributeMethod = request.getClass().getMethod("getAttribute", String.class);
            Object val = getAttributeMethod.invoke(request, attributeName);
            if (val instanceof Number) return ((Number) val).longValue();
            if (val instanceof String) return Long.parseLong((String) val);
        } catch (Exception ignored) {}
        return null;
    }

    private static class SlidingWindowCounter {
        private final int timeWindowSeconds;
        private long windowStartMs;
        private int count;

        public SlidingWindowCounter(int timeWindowSeconds) {
            this.timeWindowSeconds = timeWindowSeconds;
            this.windowStartMs = System.currentTimeMillis();
            this.count = 0;
        }

        public synchronized boolean allow(int maxCount) {
            long now = System.currentTimeMillis();
            if (now - windowStartMs > timeWindowSeconds * 1000L) {
                windowStartMs = now;
                count = 0;
            }
            if (count < maxCount) {
                count++;
                return true;
            }
            return false;
        }
    }
}
