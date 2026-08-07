package com.java2nb.novel.service.subscription;

import lombok.Data;

import java.util.Date;

@Data
public class ReadingSubscriptionRenewalAdminAuditRow {
    private Long id;
    private Long cycleId;
    private Long userId;
    private Long operatorId;
    private String action;
    private String beforeStatus;
    private String afterStatus;
    private String reason;
    private Date createTime;
}
