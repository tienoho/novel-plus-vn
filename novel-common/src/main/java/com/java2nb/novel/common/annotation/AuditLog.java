package com.java2nb.novel.common.annotation;

import java.lang.annotation.*;

/**
 * Annotation for immutable system audit logging across novel-front and novel-admin.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLog {

    /**
     * Module name (e.g. AUTH, PAYMENT, PAYOUT, KYC, CONTENT, SYSTEM).
     */
    String module() default "SYSTEM";

    /**
     * Specific security event type (e.g. AUTH_LOGIN_SUCCESS, PAYOUT_APPROVED, 2FA_ENABLE, etc.).
     */
    String eventType();

    /**
     * Detailed description or detail pattern.
     */
    String detail() default "";
}
