package com.java2nb.novel.dto.gift;

import com.java2nb.novel.service.gift.GiftRedemptionResult;

public record GiftCodeRedeemResponse(String status, long redemptionId,
                                     String rewardType, long rewardAmount) {
    public static GiftCodeRedeemResponse from(GiftRedemptionResult result) {
        return new GiftCodeRedeemResponse(result.status().name(), result.redemptionId(),
            result.rewardType(), result.rewardAmount());
    }
}
