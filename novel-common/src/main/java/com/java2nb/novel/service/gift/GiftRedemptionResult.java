package com.java2nb.novel.service.gift;

public record GiftRedemptionResult(GiftRedemptionStatus status, long redemptionId,
                                   String rewardType, long rewardAmount) {
    public static GiftRedemptionResult already(GiftRedemptionRow row) {
        return new GiftRedemptionResult(GiftRedemptionStatus.ALREADY_REDEEMED,
            row.getId(), row.getRewardType(), row.getRewardAmount());
    }
}
