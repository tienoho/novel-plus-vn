package com.java2nb.novel.service.gamification;

import lombok.Data;

import java.util.Date;

@Data
public class GamificationPublicPolicyRow {
    private Long id;
    private String policyVersion;
    private String title;
    private String contentText;
    private String status;
    private Long publishedBy;
    private Date publishedAt;
    private Long version;
}
