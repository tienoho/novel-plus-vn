package com.java2nb.novel.dto.gift;

import com.java2nb.novel.service.gift.GiftRedemptionHistoryRow;

import java.time.Instant;

public record GiftRedemptionHistoryItemResponse(long receiptId, String campaignName,
                                                String codeHint, String rewardType,
                                                long rewardAmount, Instant redeemedAt) {
    public static GiftRedemptionHistoryItemResponse from(GiftRedemptionHistoryRow row) {
        return new GiftRedemptionHistoryItemResponse(row.getId(), row.getCampaignName(),
            row.getCodeHint(), row.getRewardType(), row.getRewardAmount(),
            row.getRedeemedAt().toInstant());
    }
}
