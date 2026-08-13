package com.java2nb.novel.service.subscription;

import java.util.Date;
import lombok.Data;

@Data
public class ReadingSubscriptionMandateAdminAuditRow {
    private Long id;
    private Long mandateId;
    private Long userId;
    private Long operatorId;
    private String action;
    private String beforeStatus;
    private String afterStatus;
    private String reason;
    private Date createTime;
}
