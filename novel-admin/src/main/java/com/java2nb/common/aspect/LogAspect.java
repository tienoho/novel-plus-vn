package com.java2nb.common.aspect;

import java.lang.reflect.Method;
import java.util.Date;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.http.HttpServletRequest;

import com.java2nb.common.service.LogService;
import com.java2nb.system.domain.UserToken;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import com.java2nb.common.utils.Messages;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.java2nb.common.annotation.Log;
import com.java2nb.common.dao.LogDao;
import com.java2nb.common.domain.LogDO;
import com.java2nb.common.utils.HttpContextUtils;
import com.java2nb.common.utils.IPUtils;
import com.java2nb.common.utils.JSONUtils;
import com.java2nb.common.utils.ShiroUtils;
import com.java2nb.system.domain.UserDO;

@Aspect
@Component
public class LogAspect {
    @Autowired
    private Messages messages;
    private static final Logger logger = LoggerFactory.getLogger(LogAspect.class);

    @Autowired
    LogService logService;


    @Pointcut("@annotation(com.java2nb.common.annotation.Log)")
    public void logPointCut() {
    }

    @Around("logPointCut()")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        long beginTime = System.currentTimeMillis();
        // Phương thức thực thi
        Object result = point.proceed();
        // Thời gian thực thi (mili giây)
        long time = System.currentTimeMillis() - beginTime;
        // Lưu log bất đồng bộ
        saveLog(point, time);
        return result;
    }

    void saveLog(ProceedingJoinPoint joinPoint, long time) throws InterruptedException {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        LogDO sysLog = new LogDO();
        Log syslog = method.getAnnotation(Log.class);
        if (syslog != null) {
            // Mô tả trên annotation
            sysLog.setOperation(syslog.value());
        }
        // Tên phương thức yêu cầu
        String className = joinPoint.getTarget().getClass().getName();
        String methodName = signature.getName();
        sysLog.setMethod(className + "." + methodName + "()");
        // Tham số yêu cầu
        Object[] args = joinPoint.getArgs();
        try {
            String params = JSONUtils.beanToJson(args[0]).substring(0, 4999);
            sysLog.setParams(params);
        } catch (Exception e) {

        }
        // Lấy request
        HttpServletRequest request = HttpContextUtils.getHttpServletRequest();
        // Đặt địa chỉ IP
        sysLog.setIp(IPUtils.getIpAddr(request));
        // Tên người dùng
        UserDO currUser = ShiroUtils.getUser();
        if (null == currUser) {
            if (null != sysLog.getParams()) {
                sysLog.setUserId(-1L);
                sysLog.setUsername(sysLog.getParams());
            } else {
                sysLog.setUserId(-1L);
                sysLog.setUsername(messages.get("error.userInfoMissing"));
            }
        } else {
            sysLog.setUserId(ShiroUtils.getUserId());
            sysLog.setUsername(ShiroUtils.getUser().getUsername());
        }
        sysLog.setTime((int) time);
        // Thời gian hiện tại của hệ thống
        Date date = new Date();
        sysLog.setGmtCreate(date);
        // Lưu log hệ thống
        logService.save(sysLog);
    }
}
