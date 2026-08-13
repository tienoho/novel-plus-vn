package com.java2nb.novel.service.gamification;

import lombok.Data;

import java.util.Date;

/** Trạng thái đầy đủ của một kỳ xếp hạng Ngọn Đuốc. */
@Data
public class MonthlySeasonRow {
    private Long id;
    private String periodCode;
    private String seasonType;
    private String zoneId;
    private Date startAt;
    private Date endAt;
    private Date voteCutoffAt;
    private String status;
    private String policyVersion;
    private Long snapshotId;
    private Date closingAt;
    private Date reviewAt;
    private Date finalizedAt;
    private Long finalizedBy;
    private Long version;
}
