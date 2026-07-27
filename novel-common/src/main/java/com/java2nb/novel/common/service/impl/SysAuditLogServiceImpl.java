package com.java2nb.novel.common.service.impl;

import com.java2nb.novel.common.dao.SysAuditLogDao;
import com.java2nb.novel.common.entity.SysAuditLogDO;
import com.java2nb.novel.common.service.SysAuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysAuditLogServiceImpl implements SysAuditLogService {

    private final SysAuditLogDao sysAuditLogDao;

    @Async
    @Override
    public void saveAuditLog(SysAuditLogDO auditLog) {
        try {
            sysAuditLogDao.insert(auditLog);
        } catch (Exception e) {
            log.error("Failed to save audit log: {}", auditLog, e);
        }
    }

    @Override
    public SysAuditLogDO getById(Long id) {
        return sysAuditLogDao.selectById(id);
    }

    @Override
    public List<SysAuditLogDO> listAuditLogs(Map<String, Object> params) {
        return sysAuditLogDao.selectList(params);
    }

    @Override
    public int countAuditLogs(Map<String, Object> params) {
        return sysAuditLogDao.countList(params);
    }
}
