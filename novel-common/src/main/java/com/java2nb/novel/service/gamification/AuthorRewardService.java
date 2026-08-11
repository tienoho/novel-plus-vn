package com.java2nb.novel.service.gamification;

import java.util.Date;
import java.util.List;

public interface AuthorRewardService {

    RewardCampaignRow calculateAllocations(RewardCampaignCommand command);

    RewardCampaignRow approveCampaign(long campaignId, long approvedBy, Date approvedAt);

    List<Long> listApprovedAllocationIds(long campaignId, int limit);

    AuthorRewardAllocationRow postPendingReward(long allocationId, Date postedAt,
                                                 int claimWindowDays);

    List<Long> listMaturedAllocationIds(Date postedBefore, int limit);

    AuthorRewardAllocationRow releaseMaturedReward(long allocationId, Date releasedAt);

    AuthorRewardAllocationRow clawback(long allocationId, long operatorId, String reason,
                                       Date clawedBackAt);

    List<AuthorRewardAllocationRow> listAuthorRewards(long authorId, int limit);
}
