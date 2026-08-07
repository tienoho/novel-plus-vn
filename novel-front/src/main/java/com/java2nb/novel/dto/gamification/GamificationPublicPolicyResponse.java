package com.java2nb.novel.dto.gamification;

import com.java2nb.novel.service.gamification.GamificationPublicPolicyRow;

import java.util.Date;

public record GamificationPublicPolicyResponse(
    String policyVersion,
    String title,
    String contentText,
    Date publishedAt
) {
    public static GamificationPublicPolicyResponse from(GamificationPublicPolicyRow source) {
        return new GamificationPublicPolicyResponse(source.getPolicyVersion(), source.getTitle(),
            source.getContentText(), source.getPublishedAt());
    }
}
