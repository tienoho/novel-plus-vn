package com.java2nb.common.aspect;

import com.java2nb.common.annotation.SanitizeMap;
import com.java2nb.common.utils.SortWhitelistUtil;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;

/**
 * Chặn các phương thức list* của Mapper để chuẩn hóa trường và thứ tự sắp xếp trong tham số Map có @SanitizeMap.
 *
 * <p>Chủ yếu ngăn SQL injection, trường hoặc thứ tự sắp xếp không hợp lệ.
 * Ví dụ, lọc theo danh sách cho phép và chuẩn hóa các trường sort và order.</p>
 */
@Aspect
@Component
@RequiredArgsConstructor
public class MapSortValidationAspect {

    /**
     * Chặn các phương thức list* của Mapper (như list(), listByPage).
     * Xử lý tham số Map có annotation @SanitizeMap.
     *
     * <p>Luồng xử lý:</p>
     * <ol>
     *   <li>Lấy tham số phương thức và thông tin annotation</li>
     *   <li>Duyệt mọi tham số và kiểm tra annotation @SanitizeMap</li>
     *   <li>Nếu tham số là Map có annotation thì làm sạch trường</li>
     * </ol>
     *
     * @param joinPoint thông tin join point
     * @return kết quả thực thi phương thức
     */
    @SneakyThrows
    @Around("execution(* com.java2nb.*.dao.*Dao.list*(..))")
    public Object sanitizeMapParameters(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        Annotation[][] parameterAnnotations = method.getParameterAnnotations();

        for (int i = 0; i < parameterAnnotations.length; i++) {
            boolean hasAnnotation = Arrays.stream(parameterAnnotations[i])
                .anyMatch(a -> a.annotationType().equals(SanitizeMap.class));

            if (hasAnnotation && args[i] instanceof Map map) {
                if (map.get("sort") instanceof String sortStr) {
                    map.put("sort", SortWhitelistUtil.sanitizeColumn(sortStr));
                }
                if (map.get("order") instanceof String orderStr) {
                    map.put("order", SortWhitelistUtil.sanitizeOrder(orderStr));
                }
            }
        }

        return joinPoint.proceed(args);
    }

}
