package com.java2nb.novel.service.gamification;

import java.util.List;

public record RewardCampaignCommand(long seasonId, long budgetXu, List<RewardShareRule> shares,
                                    String policyVersion) {
    public RewardCampaignCommand {
        shares = shares == null ? List.of() : List.copyOf(shares);
    }
}
