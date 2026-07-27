package com.java2nb.novel.controller;

import com.java2nb.common.utils.PageBean;
import com.java2nb.common.utils.Query;
import com.java2nb.common.utils.R;
import com.java2nb.novel.common.entity.SysAuditLogDO;
import com.java2nb.novel.common.service.SysAuditLogService;
import lombok.RequiredArgsConstructor;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/system/auditLog")
@RequiredArgsConstructor
public class SecurityAuditLogAdminController {

    private final SysAuditLogService sysAuditLogService;

    @GetMapping("/list")
    @RequiresPermissions("sys:auditLog:view")
    public PageBean list(@RequestParam Map<String, Object> params) {
        Query query = new Query(params);
        List<SysAuditLogDO> list = sysAuditLogService.listAuditLogs(query);
        int total = sysAuditLogService.countAuditLogs(query);
        return new PageBean(list, total);
    }

    @GetMapping("/{id}")
    @RequiresPermissions("sys:auditLog:view")
    public R getById(@PathVariable Long id) {
        SysAuditLogDO auditLog = sysAuditLogService.getById(id);
        if (auditLog == null) {
            return R.error("Khong tim thay nhat ky kiem toan ID: " + id);
        }
        return R.ok().put("auditLog", auditLog);
    }
}
