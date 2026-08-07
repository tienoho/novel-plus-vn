package com.java2nb.common.aspect;

import com.java2nb.common.utils.IPUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;

@Aspect
@Component
public class WebLogAspect {

    private static final Logger logger = LoggerFactory.getLogger(WebLogAspect.class);

    @Pointcut("execution( * com.java2nb..controller.*.*(..))")//Hai dấu chấm đại diện mọi thư mục con; hai dấu chấm trong ngoặc cuối đại diện mọi tham số
    public void logPointCut() {
    }


    @Before("logPointCut()")
    public void doBefore(JoinPoint joinPoint) throws Throwable {
        // Nhận yêu cầu và ghi lại nội dung
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes.getRequest();

        // Ghi lại nội dung yêu cầu
        logger.info("Địa chỉ yêu cầu: " + request.getRequestURL().toString());
        logger.info("HTTP METHOD : " + request.getMethod());
        // Lấy địa chỉ IP thực
        logger.info("IP : " + IPUtils.getIpAddr(request));
        logger.info("CLASS_METHOD : " + joinPoint.getSignature().getDeclaringTypeName() + "."
            + joinPoint.getSignature().getName());
        logger.info("Tham số: " + Arrays.toString(joinPoint.getArgs()));

    }

    @AfterReturning(returning = "ret", pointcut = "logPointCut()")// Giá trị returning trùng tên tham số của doAfterReturning
    public void doAfterReturning(Object ret) throws Throwable {
        // Không ghi payload phản hồi vì có thể chứa mã quà, token hoặc dữ liệu cá nhân.
        logger.debug("Yêu cầu đã được xử lý thành công");
    }

    @Around("logPointCut()")
    public Object doAround(ProceedingJoinPoint pjp) throws Throwable {
        long startTime = System.currentTimeMillis();
        Object ob = pjp.proceed();// ob là giá trị trả về của phương thức
        logger.info("Thời gian xử lý: " + (System.currentTimeMillis() - startTime));
        return ob;
    }
}
