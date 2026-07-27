package com.java2nb.novel.common.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

/**
 * Domain entity for table sys_audit_log.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SysAuditLogDO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String module;
    private String eventType;
    private Long actorId;
    private String actorUsername;
    private String actorIp;
    private String userAgent;
    private String requestUrl;
    private String requestParams;
    private String status; // SUCCESS or FAILURE
    private String detail;
    private Date createTime;
}
