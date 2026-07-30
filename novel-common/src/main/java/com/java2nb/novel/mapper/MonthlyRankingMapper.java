package com.java2nb.novel.mapper;

import com.java2nb.novel.service.gamification.MonthlySeasonRow;
import com.java2nb.novel.service.gamification.MonthlyRankRow;
import com.java2nb.novel.service.gamification.MonthlyRankSnapshotRow;
import com.java2nb.novel.service.gamification.MonthlyRankDriftRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

@Mapper
public interface MonthlyRankingMapper {

    MonthlySeasonRow selectSeasonById(@Param("seasonId") long seasonId);

    int insertRegularSeasonIgnore(@Param("periodCode") String periodCode,
                                  @Param("zoneId") String zoneId,
                                  @Param("startAt") Date startAt,
                                  @Param("endAt") Date endAt,
                                  @Param("voteCutoffAt") Date voteCutoffAt,
                                  @Param("policyVersion") String policyVersion);

    MonthlySeasonRow selectSeasonByPeriod(@Param("periodCode") String periodCode);

    MonthlySeasonRow selectSeasonAt(@Param("at") Date at);

    MonthlySeasonRow selectLatestSeason();

    List<MonthlySeasonRow> selectSeasonsReadyToClose(@Param("at") Date at,
                                                     @Param("limit") int limit);

    List<MonthlySeasonRow> selectClosingSeasons(@Param("limit") int limit);

    int claimSeasonClosing(@Param("seasonId") long seasonId,
                           @Param("expectedVersion") long expectedVersion,
                           @Param("closingAt") Date closingAt);

    int insertInitialSnapshot(@Param("seasonId") long seasonId,
                              @Param("cutoffAt") Date cutoffAt);

    MonthlyRankSnapshotRow selectInitialSnapshot(@Param("seasonId") long seasonId);

    MonthlyRankSnapshotRow selectInitialSnapshotById(@Param("snapshotId") long snapshotId);

    List<MonthlyRankRow> selectSourceRankingPage(@Param("seasonId") long seasonId,
                                                 @Param("cutoffAt") Date cutoffAt,
                                                 @Param("offset") long offset,
                                                 @Param("limit") int limit);

    int insertRankEntries(@Param("rows") List<MonthlyRankRow> rows);

    List<MonthlyRankRow> selectSnapshotEntryPage(@Param("snapshotId") long snapshotId,
                                                  @Param("afterRank") int afterRank,
                                                  @Param("limit") int limit);

    int sealSnapshot(@Param("snapshotId") long snapshotId,
                     @Param("entryCount") int entryCount,
                     @Param("totalTickets") long totalTickets,
                     @Param("contentHash") String contentHash,
                     @Param("sealedAt") Date sealedAt);

    int claimSeasonReview(@Param("seasonId") long seasonId,
                          @Param("expectedVersion") long expectedVersion,
                          @Param("snapshotId") long snapshotId,
                          @Param("reviewAt") Date reviewAt);

    int insertJobRunIgnore(@Param("jobType") String jobType,
                           @Param("scopeType") String scopeType,
                           @Param("scopeKey") String scopeKey,
                           @Param("ownerInstance") String ownerInstance,
                           @Param("startedAt") Date startedAt);

    int claimStaleJobRun(@Param("jobType") String jobType,
                         @Param("scopeType") String scopeType,
                         @Param("scopeKey") String scopeKey,
                         @Param("ownerInstance") String ownerInstance,
                         @Param("claimedAt") Date claimedAt,
                         @Param("staleBefore") Date staleBefore);

    String selectJobCheckpoint(@Param("jobType") String jobType,
                               @Param("scopeType") String scopeType,
                               @Param("scopeKey") String scopeKey,
                               @Param("ownerInstance") String ownerInstance);

    int advanceJobCheckpoint(@Param("jobType") String jobType,
                             @Param("scopeType") String scopeType,
                             @Param("scopeKey") String scopeKey,
                             @Param("ownerInstance") String ownerInstance,
                             @Param("checkpoint") String checkpoint,
                             @Param("processedDelta") long processedDelta,
                             @Param("heartbeatAt") Date heartbeatAt);

    int completeJobRun(@Param("jobType") String jobType,
                       @Param("scopeType") String scopeType,
                       @Param("scopeKey") String scopeKey,
                       @Param("ownerInstance") String ownerInstance,
                       @Param("finishedAt") Date finishedAt);

    int completeTerminalSnapshotJob(@Param("scopeKey") String scopeKey,
                                    @Param("finishedAt") Date finishedAt);

    int failJobRun(@Param("jobType") String jobType,
                   @Param("scopeType") String scopeType,
                   @Param("scopeKey") String scopeKey,
                   @Param("ownerInstance") String ownerInstance,
                   @Param("finishedAt") Date finishedAt,
                   @Param("errorMessage") String errorMessage);

    long countLiveRanking(@Param("seasonId") long seasonId);

    List<MonthlyRankRow> selectLiveRankingPage(@Param("seasonId") long seasonId,
                                               @Param("offset") long offset,
                                               @Param("limit") int limit);

    long countSnapshotRanking(@Param("snapshotId") long snapshotId);

    List<MonthlyRankRow> selectPublicSnapshotPage(@Param("snapshotId") long snapshotId,
                                                  @Param("offset") long offset,
                                                  @Param("limit") int limit);

    int pauseSnapshotJob(@Param("scopeKey") String scopeKey,
                         @Param("pausedAt") Date pausedAt,
                         @Param("reason") String reason);

    int insertPausedSnapshotJobIgnore(@Param("scopeKey") String scopeKey,
                                      @Param("pausedAt") Date pausedAt,
                                      @Param("reason") String reason);

    int resumeSnapshotJob(@Param("scopeKey") String scopeKey,
                          @Param("resumedAt") Date resumedAt);

    List<MonthlyRankDriftRow> checkRankCounterDrift(@Param("seasonId") Long seasonId);

    int claimSeasonFinalized(@Param("seasonId") long seasonId,
                             @Param("expectedVersion") long expectedVersion,
                             @Param("snapshotId") long snapshotId,
                             @Param("operatorId") long operatorId,
                             @Param("finalizedAt") Date finalizedAt);
}
