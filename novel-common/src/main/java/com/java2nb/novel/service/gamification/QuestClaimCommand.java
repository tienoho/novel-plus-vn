package com.java2nb.novel.service.gamification;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

public record QuestClaimCommand(long userId, String questCode, LocalDate localDate, Date claimedAt,
                                ZoneId zoneId, int ticketValidityDays, String ruleVersion,
                                String policyVersion) {
    public QuestClaimCommand {
        if (userId <= 0 || questCode == null || questCode.isBlank() || questCode.length() > 48) {
            throw new IllegalArgumentException("Chủ thể hoặc mã nhiệm vụ không hợp lệ");
        }
        if (localDate == null || claimedAt == null || zoneId == null || ticketValidityDays <= 0) {
            throw new IllegalArgumentException("Thiếu thời điểm hoặc chính sách nhận thưởng nhiệm vụ");
        }
        if (ruleVersion == null || ruleVersion.isBlank() || ruleVersion.length() > 32
            || policyVersion == null || policyVersion.isBlank() || policyVersion.length() > 32) {
            throw new IllegalArgumentException("Phiên bản luật hoặc chính sách nhiệm vụ không hợp lệ");
        }
        claimedAt = new Date(claimedAt.getTime());
    }

    @Override
    public Date claimedAt() {
        return new Date(claimedAt.getTime());
    }
}
