package com.java2nb.novel.service.entitlement;

import lombok.Data;

import java.util.Date;

@Data
public class ChapterEntitlementRow {
    private Long id;
    private Long userId;
    private Long bookId;
    private Long bookIndexId;
    private String sourceType;
    private String sourceId;
    private Date validFrom;
    private Date validUntil;
    private String status;
    private String idempotencyKey;
    private String policyVersion;
}
