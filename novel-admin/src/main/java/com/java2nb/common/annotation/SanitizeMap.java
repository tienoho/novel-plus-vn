package com.java2nb.common.annotation;

import java.lang.annotation.*;

/**
 * Đánh dấu tham số phương thức cần làm sạch và chuẩn hóa các trường Map.
 *
 * <p>Thường dùng cho tham số Map của phương thức list trong DAO để ngăn trường hoặc thứ tự sắp xếp không hợp lệ.</p>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SanitizeMap {
}
