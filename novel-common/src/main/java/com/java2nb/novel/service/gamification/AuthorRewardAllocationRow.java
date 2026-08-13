package com.java2nb.novel.service.gamification;

import lombok.Data;

import java.util.Date;

@Data
public class AuthorRewardAllocationRow {
    private Long id;
    private String allocationNo;
    private Long campaignId;
    private Long seasonId;
    private String periodCode;
    private Long snapshotId;
    private Long bookId;
    private String bookName;
    private Long authorId;
    private Integer rankNo;
    private Long amountXu;
    private Long roundingAdjustmentXu;
    private String status;
    private Date postedAt;
    private Date releasedAt;
    private Date clawedBackAt;
    private String reason;
    private String policyVersion;
    private Long version;
}
