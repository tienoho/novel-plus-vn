package com.java2nb.novel.mapper;

import com.java2nb.novel.service.gamification.GamificationPublicPolicyRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface GamificationPublicPolicyMapper {
    GamificationPublicPolicyRow selectPublished();
    GamificationPublicPolicyRow selectByVersion(@Param("policyVersion") String policyVersion);
    GamificationPublicPolicyRow lockById(@Param("policyId") long policyId);
    GamificationPublicPolicyRow selectById(@Param("policyId") long policyId);

    int insertDraft(@Param("policyVersion") String policyVersion,
                    @Param("title") String title,
                    @Param("contentText") String contentText);

    int archivePublished(@Param("archivedAt") java.util.Date archivedAt);

    int publish(@Param("policyId") long policyId,
                @Param("expectedVersion") long expectedVersion,
                @Param("operatorId") long operatorId,
                @Param("publishedAt") java.util.Date publishedAt);

    int insertAudit(@Param("policyId") long policyId,
                    @Param("eventType") String eventType,
                    @Param("fromStatus") String fromStatus,
                    @Param("toStatus") String toStatus,
                    @Param("operatorId") long operatorId);
}
