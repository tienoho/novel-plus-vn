package com.java2nb.novel.service.gamification;

import java.util.List;

public record RewardCampaignCommand(long seasonId, long budgetXu, List<RewardShareRule> shares,
                                    String policyVersion, long runtimeConfigRevision) {
    public RewardCampaignCommand {
        shares = shares == null ? List.of() : List.copyOf(shares);
        if (runtimeConfigRevision <= 0) {
            throw new IllegalArgumentException("Revision cấu hình gamification không hợp lệ");
        }
    }
}
