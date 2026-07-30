package com.java2nb.novel.mapper;

import com.java2nb.novel.service.gamification.AuthorRewardAllocationRow;
import com.java2nb.novel.service.gamification.MonthlyRankRow;
import com.java2nb.novel.service.gamification.MonthlySeasonRow;
import com.java2nb.novel.service.gamification.RewardCampaignRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

@Mapper
public interface AuthorRewardMapper {

    MonthlySeasonRow selectSeasonForReward(@Param("seasonId") long seasonId);

    MonthlySeasonRow selectSeasonByPeriodForReward(@Param("periodCode") String periodCode);

    List<MonthlyRankRow> selectSnapshotRewardRows(@Param("snapshotId") long snapshotId,
                                                   @Param("limit") int limit);

    int insertCampaign(@Param("periodCode") String periodCode,
                       @Param("budgetXu") long budgetXu,
                       @Param("structureJson") String structureJson,
                       @Param("policyVersion") String policyVersion);

    RewardCampaignRow selectCampaignByPeriod(@Param("periodCode") String periodCode);

    RewardCampaignRow lockCampaignById(@Param("campaignId") long campaignId);

    int insertAllocation(AuthorRewardAllocationRow row);

    int approveCampaign(@Param("campaignId") long campaignId,
                        @Param("expectedVersion") long expectedVersion,
                        @Param("approvedBy") long approvedBy,
                        @Param("approvedAt") Date approvedAt);

    int approveAllocations(@Param("campaignId") long campaignId);

    int attachCampaignToSeason(@Param("seasonId") long seasonId,
                               @Param("campaignId") long campaignId);

    List<Long> selectApprovedAllocationIds(@Param("campaignId") long campaignId,
                                            @Param("limit") int limit);

    AuthorRewardAllocationRow lockAllocationById(@Param("allocationId") long allocationId);

    AuthorRewardAllocationRow selectAllocationById(@Param("allocationId") long allocationId);

    int markPostedPending(@Param("allocationId") long allocationId,
                          @Param("expectedVersion") long expectedVersion,
                          @Param("postedAt") Date postedAt);

    List<Long> selectMaturedAllocationIds(@Param("postedBefore") Date postedBefore,
                                           @Param("limit") int limit);

    int markReleased(@Param("allocationId") long allocationId,
                     @Param("expectedVersion") long expectedVersion,
                     @Param("releasedAt") Date releasedAt);

    int markClawedBack(@Param("allocationId") long allocationId,
                       @Param("expectedVersion") long expectedVersion,
                       @Param("clawedBackAt") Date clawedBackAt,
                       @Param("reason") String reason);

    List<AuthorRewardAllocationRow> selectAuthorRewards(@Param("authorId") long authorId,
                                                         @Param("limit") int limit);
}
