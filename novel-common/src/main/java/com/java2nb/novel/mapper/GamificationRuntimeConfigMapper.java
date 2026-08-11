package com.java2nb.novel.mapper;

import com.java2nb.novel.service.gamification.config.GamificationRuntimeConfigRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

@Mapper
public interface GamificationRuntimeConfigMapper {
    GamificationRuntimeConfigRow selectActive();

    GamificationRuntimeConfigRow selectById(@Param("id") long id);

    GamificationRuntimeConfigRow lockById(@Param("id") long id);

    GamificationRuntimeConfigRow selectBySourceHash(@Param("sourceHash") String sourceHash);

    GamificationRuntimeConfigRow lockDueScheduled(@Param("now") Date now);

    List<GamificationRuntimeConfigRow> selectHistory(@Param("limit") int limit);

    long selectNextRevisionNo();

    boolean isPolicyPublished(@Param("policyVersion") String policyVersion);

    Integer selectActiveReadingQuestTarget(@Param("policyVersion") String policyVersion);

    boolean hasPublishedPublicPolicy(@Param("policyVersion") String policyVersion);

    boolean hasApprovedRewardCampaign(@Param("policyVersion") String policyVersion);

    Date selectLatestOpenSeasonEnd();

    int insertDraft(GamificationRuntimeConfigRow row);

    int saveDraft(@Param("row") GamificationRuntimeConfigRow row,
                  @Param("expectedVersion") long expectedVersion);

    int submit(@Param("id") long id, @Param("expectedVersion") long expectedVersion,
               @Param("activationClass") String activationClass,
               @Param("highRisk") boolean highRisk, @Param("operatorId") long operatorId,
               @Param("submittedAt") Date submittedAt, @Param("reason") String reason);

    int approve(@Param("id") long id, @Param("expectedVersion") long expectedVersion,
                @Param("operatorId") long operatorId, @Param("approvedAt") Date approvedAt);

    int reject(@Param("id") long id, @Param("expectedVersion") long expectedVersion,
               @Param("operatorId") long operatorId, @Param("reason") String reason);

    int schedule(@Param("id") long id, @Param("expectedVersion") long expectedVersion,
                 @Param("operatorId") long operatorId, @Param("effectiveAt") Date effectiveAt,
                 @Param("reason") String reason);

    int cancel(@Param("id") long id, @Param("expectedVersion") long expectedVersion,
               @Param("operatorId") long operatorId, @Param("reason") String reason);

    int archiveActive(@Param("id") long id, @Param("expectedVersion") long expectedVersion,
                      @Param("archivedAt") Date archivedAt);

    int activateScheduled(@Param("id") long id, @Param("expectedVersion") long expectedVersion,
                          @Param("operatorId") long operatorId, @Param("activatedAt") Date activatedAt);

    int insertAudit(@Param("configId") long configId, @Param("eventType") String eventType,
                    @Param("fromStatus") String fromStatus, @Param("toStatus") String toStatus,
                    @Param("operatorId") long operatorId, @Param("reason") String reason,
                    @Param("beforeHash") String beforeHash, @Param("afterHash") String afterHash,
                    @Param("diffJson") String diffJson);
}
