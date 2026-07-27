package com.java2nb.novel.common.service;

import com.java2nb.novel.common.entity.SysAuditLogDO;

import java.util.List;
import java.util.Map;

public interface SysAuditLogService {

    void saveAuditLog(SysAuditLogDO auditLog);

    SysAuditLogDO getById(Long id);

    List<SysAuditLogDO> listAuditLogs(Map<String, Object> params);

    int countAuditLogs(Map<String, Object> params);
}
