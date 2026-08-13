package com.java2nb.novel.service.gamification;

import java.util.Date;

public record QuestCampaignDraftCommand(String campaignCode, Date startAt, Date endAt,
                                        String policyVersion) {
    public QuestCampaignDraftCommand {
        campaignCode = campaignCode == null ? "" : campaignCode.trim().toUpperCase(java.util.Locale.ROOT);
        policyVersion = policyVersion == null ? "" : policyVersion.trim();
        if (campaignCode.equals("DEFAULT") || !campaignCode.matches("[A-Z0-9_]{3,48}")) {
            throw new IllegalArgumentException("Mã campaign nhiệm vụ không hợp lệ");
        }
        if (startAt == null || endAt == null || !endAt.after(startAt)) {
            throw new IllegalArgumentException("Cửa sổ campaign nhiệm vụ không hợp lệ");
        }
        if (policyVersion.isBlank() || policyVersion.length() > 32) {
            throw new IllegalArgumentException("Phiên bản chính sách campaign không hợp lệ");
        }
        startAt = new Date(startAt.getTime());
        endAt = new Date(endAt.getTime());
    }

    @Override
    public Date startAt() {
        return new Date(startAt.getTime());
    }

    @Override
    public Date endAt() {
        return new Date(endAt.getTime());
    }
}
