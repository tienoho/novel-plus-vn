package com.java2nb.novel.service.gift;

import java.util.Date;
import java.util.Locale;

public record GiftCampaignCommand(String campaignCode, String campaignName,
                                  String rewardType, long rewardAmount,
                                  Integer ticketValidityDays, Date startAt, Date endAt,
                                  long maxRedemptions, int maxPerUser) {
    public GiftCampaignCommand {
        campaignCode = campaignCode == null ? "" : campaignCode.trim().toUpperCase(Locale.ROOT);
        campaignName = campaignName == null ? "" : campaignName.trim();
        rewardType = rewardType == null ? "" : rewardType.trim().toUpperCase(Locale.ROOT);
        boolean ticketReward = "READING_TICKET".equals(rewardType);
        if (!campaignCode.matches("[A-Z0-9_]{3,32}")
            || campaignName.length() < 3 || campaignName.length() > 100
            || (!"XU".equals(rewardType) && !ticketReward)
            || rewardAmount < 1 || rewardAmount > 100_000
            || (ticketReward && (ticketValidityDays == null
                || ticketValidityDays < 1 || ticketValidityDays > 366))
            || (!ticketReward && ticketValidityDays != null)
            || startAt == null || endAt == null || !endAt.after(startAt)
            || maxRedemptions < 1 || maxPerUser < 1 || maxPerUser > 100) {
            throw new IllegalArgumentException("Cấu hình chiến dịch mã quà không hợp lệ");
        }
        startAt = new Date(startAt.getTime());
        endAt = new Date(endAt.getTime());
    }

    @Override public Date startAt() { return new Date(startAt.getTime()); }
    @Override public Date endAt() { return new Date(endAt.getTime()); }
}
