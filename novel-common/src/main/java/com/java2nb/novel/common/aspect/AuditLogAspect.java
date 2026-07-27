package com.java2nb.novel.common.aspect;

import com.java2nb.novel.common.annotation.AuditLog;
import com.java2nb.novel.common.entity.SysAuditLogDO;
import com.java2nb.novel.common.service.SysAuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.lang.reflect.Method;
import java.util.Arrays;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private final SysAuditLogService sysAuditLogService;

    @Pointcut("@annotation(auditLog)")
    public void auditLogPointcut(AuditLog auditLog) {}

    @Around("auditLogPointcut(auditLog)")
    public Object around(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        String status = "SUCCESS";
        String detail = auditLog.detail();
        Object result = null;

        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable t) {
            status = "FAILURE";
            detail = StringUtils.defaultIfBlank(detail, t.getMessage());
            throw t;
        } finally {
            try {
                Object requestObj = getHttpServletRequest();
                String ip = getClientIp(requestObj);
                String userAgent = getHeader(requestObj, "User-Agent");
                String requestUrl = getRequestUri(requestObj);
                if (StringUtils.isBlank(requestUrl)) {
                    requestUrl = joinPoint.getSignature().toShortString();
                }
                String params = getRequestParams(joinPoint);

                Long actorId = getAttributeLong(requestObj, "userId");
                if (actorId == null) actorId = getAttributeLong(requestObj, "actorId");

                String actorUsername = getAttributeString(requestObj, "username");
                if (StringUtils.isBlank(actorUsername)) actorUsername = getAttributeString(requestObj, "actorUsername");
                if (StringUtils.isBlank(actorUsername)) actorUsername = "SYSTEM";

                SysAuditLogDO logDO = SysAuditLogDO.builder()
                        .module(auditLog.module())
                        .eventType(auditLog.eventType())
                        .actorId(actorId)
                        .actorUsername(actorUsername)
                        .actorIp(ip)
                        .userAgent(userAgent != null && userAgent.length() > 500 ? userAgent.substring(0, 500) : userAgent)
                        .requestUrl(requestUrl)
                        .requestParams(params != null && params.length() > 2000 ? params.substring(0, 2000) : params)
                        .status(status)
                        .detail(detail)
                        .build();

                sysAuditLogService.saveAuditLog(logDO);
            } catch (Exception e) {
                log.error("Error creating audit log in aspect", e);
            }
        }
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

    private String getHeader(Object request, String headerName) {
        if (request == null) return null;
        try {
            Method getHeaderMethod = request.getClass().getMethod("getHeader", String.class);
            return (String) getHeaderMethod.invoke(request, headerName);
        } catch (Exception e) {
            return null;
        }
    }

    private String getRequestUri(Object request) {
        if (request == null) return null;
        try {
            Method getUriMethod = request.getClass().getMethod("getRequestURI");
            return (String) getUriMethod.invoke(request);
        } catch (Exception e) {
            return null;
        }
    }

    private String getClientIp(Object request) {
        if (request == null) return "127.0.0.1";
        String ip = getHeader(request, "X-Forwarded-For");
        if (StringUtils.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = getHeader(request, "Proxy-Client-IP");
        }
        if (StringUtils.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = getHeader(request, "WL-Proxy-Client-IP");
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

    private String getAttributeString(Object request, String attributeName) {
        if (request == null) return null;
        try {
            Method getAttributeMethod = request.getClass().getMethod("getAttribute", String.class);
            Object val = getAttributeMethod.invoke(request, attributeName);
            return val != null ? val.toString() : null;
        } catch (Exception ignored) {}
        return null;
    }

    private String getRequestParams(ProceedingJoinPoint joinPoint) {
        try {
            Object[] args = joinPoint.getArgs();
            if (args == null || args.length == 0) return "";
            return Arrays.toString(args);
        } catch (Exception e) {
            return "PARAMS_ERROR";
        }
    }
}
