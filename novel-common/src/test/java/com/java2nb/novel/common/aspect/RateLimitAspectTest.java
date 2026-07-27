package com.java2nb.novel.common.aspect;

import com.java2nb.novel.common.annotation.LimitType;
import com.java2nb.novel.common.annotation.RateLimit;
import com.java2nb.novel.core.exception.BusinessException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class RateLimitAspectTest {

    private RateLimitAspect rateLimitAspect;

    @BeforeEach
    public void setUp() {
        rateLimitAspect = new RateLimitAspect();
    }

    @Test
    public void testRateLimitAllowsCallsWithinLimit() throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);

        when(signature.getDeclaringTypeName()).thenReturn("com.java2nb.novel.controller.UserController");
        when(signature.getName()).thenReturn("login");
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.proceed()).thenReturn("PROCEEDED");

        RateLimit rateLimit = mock(RateLimit.class);
        when(rateLimit.key()).thenReturn("login_test_1");
        when(rateLimit.count()).thenReturn(3);
        when(rateLimit.timeWindowSeconds()).thenReturn(60);
        when(rateLimit.limitType()).thenReturn(LimitType.IP);
        when(rateLimit.message()).thenReturn("Over rate limit");

        // 3 calls within limit should succeed
        assertEquals("PROCEEDED", rateLimitAspect.around(joinPoint, rateLimit));
        assertEquals("PROCEEDED", rateLimitAspect.around(joinPoint, rateLimit));
        assertEquals("PROCEEDED", rateLimitAspect.around(joinPoint, rateLimit));
    }

    @Test
    public void testRateLimitBlocksCallsExceedingLimit() throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);

        when(signature.getDeclaringTypeName()).thenReturn("com.java2nb.novel.controller.PayController");
        when(signature.getName()).thenReturn("vnpay");
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.proceed()).thenReturn("PAYMENT_URL");

        RateLimit rateLimit = mock(RateLimit.class);
        when(rateLimit.key()).thenReturn("vnpay_test_exceed");
        when(rateLimit.count()).thenReturn(2);
        when(rateLimit.timeWindowSeconds()).thenReturn(60);
        when(rateLimit.limitType()).thenReturn(LimitType.USER);
        when(rateLimit.message()).thenReturn("Yêu cầu quá nhiều, vui lòng thử lại sau.");

        // First 2 calls succeed
        rateLimitAspect.around(joinPoint, rateLimit);
        rateLimitAspect.around(joinPoint, rateLimit);

        // 3rd call exceeds limit and must throw BusinessException
        BusinessException ex = assertThrows(BusinessException.class, () -> rateLimitAspect.around(joinPoint, rateLimit));
        assertTrue(ex.getMessage().contains("Yêu cầu quá nhiều"));
    }
}
