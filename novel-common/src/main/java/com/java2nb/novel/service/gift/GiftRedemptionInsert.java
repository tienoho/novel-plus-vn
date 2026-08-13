package com.java2nb.novel.service.gift;

import java.util.Date;

public record GiftRedemptionInsert(long campaignId, long codeId, long userId,
                                   String rewardType, long rewardAmount,
                                   String clientRequestId, Date redeemedAt,
                                   String policyVersion) {
}
