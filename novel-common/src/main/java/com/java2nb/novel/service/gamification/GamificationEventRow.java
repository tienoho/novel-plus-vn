package com.java2nb.novel.service.gamification;

import lombok.Data;

import java.time.LocalDate;
import java.util.Date;

@Data
public class GamificationEventRow {

    private Long id;
    private String eventType;
    private String sourceKey;
    private Long userId;
    private Long bookId;
    private Date occurredAt;
    private LocalDate localDate;
    private String payloadHash;
    private String payloadJson;
    private String status;
    private Integer attempt;
    private Date processedAt;
    private String errorMessage;
    private String policyVersion;
    private Long runtimeConfigRevision;
    private Long version;
    private Date createTime;
}
