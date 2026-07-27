package com.java2nb.novel.common.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    String key() default "";

    int count() default 10;

    int timeWindowSeconds() default 60;

    LimitType limitType() default LimitType.IP;

    String message() default "Yêu cầu quá nhiều, vui lòng thử lại sau.";
}
