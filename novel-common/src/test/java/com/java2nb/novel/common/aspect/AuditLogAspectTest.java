package com.java2nb.novel.common.aspect;

import com.java2nb.novel.common.annotation.AuditLog;
import com.java2nb.novel.common.entity.SysAuditLogDO;
import com.java2nb.novel.common.service.SysAuditLogService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class AuditLogAspectTest {

    private SysAuditLogService sysAuditLogService;
    private AuditLogAspect auditLogAspect;

    @BeforeEach
    public void setUp() {
        sysAuditLogService = mock(SysAuditLogService.class);
        auditLogAspect = new AuditLogAspect(sysAuditLogService);
    }

    @Test
    public void testAroundSuccessLog() throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);

        when(signature.toShortString()).thenReturn("TestController.login()");
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"testuser"});
        when(joinPoint.proceed()).thenReturn("SUCCESS_RESULT");

        AuditLog auditLog = mock(AuditLog.class);
        when(auditLog.module()).thenReturn("AUTH");
        when(auditLog.eventType()).thenReturn("USER_LOGIN");
        when(auditLog.detail()).thenReturn("User login attempt");

        Object result = auditLogAspect.around(joinPoint, auditLog);

        assertEquals("SUCCESS_RESULT", result);

        ArgumentCaptor<SysAuditLogDO> captor = ArgumentCaptor.forClass(SysAuditLogDO.class);
        verify(sysAuditLogService, times(1)).saveAuditLog(captor.capture());

        SysAuditLogDO logDO = captor.getValue();
        assertEquals("AUTH", logDO.getModule());
        assertEquals("USER_LOGIN", logDO.getEventType());
        assertEquals("SUCCESS", logDO.getStatus());
        assertEquals("User login attempt", logDO.getDetail());
    }

    @Test
    public void testAroundFailureLog() throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);

        when(signature.toShortString()).thenReturn("PayoutController.requestWithdrawal()");
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"invalid_amount"});
        when(joinPoint.proceed()).thenThrow(new IllegalArgumentException("So tien rut khong hop le"));

        AuditLog auditLog = mock(AuditLog.class);
        when(auditLog.module()).thenReturn("PAYOUT");
        when(auditLog.eventType()).thenReturn("WITHDRAWAL_REQUEST");
        when(auditLog.detail()).thenReturn("");

        assertThrows(IllegalArgumentException.class, () -> auditLogAspect.around(joinPoint, auditLog));

        ArgumentCaptor<SysAuditLogDO> captor = ArgumentCaptor.forClass(SysAuditLogDO.class);
        verify(sysAuditLogService, times(1)).saveAuditLog(captor.capture());

        SysAuditLogDO logDO = captor.getValue();
        assertEquals("PAYOUT", logDO.getModule());
        assertEquals("WITHDRAWAL_REQUEST", logDO.getEventType());
        assertEquals("FAILURE", logDO.getStatus());
        assertTrue(logDO.getDetail().contains("So tien rut khong hop le"));
    }
}
