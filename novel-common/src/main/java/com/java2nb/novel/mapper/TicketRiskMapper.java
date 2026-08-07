package com.java2nb.novel.mapper;

import com.java2nb.novel.service.gamification.TicketRiskAssessmentRow;
import com.java2nb.novel.service.gamification.TicketRiskPolicyRow;
import com.java2nb.novel.service.gamification.TicketRiskRuleRow;
import com.java2nb.novel.service.gamification.TicketRiskReviewRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

@Mapper
public interface TicketRiskMapper {
    TicketRiskPolicyRow selectPolicy(@Param("policyVersion") String policyVersion);

    List<TicketRiskRuleRow> selectRules(@Param("policyVersion") String policyVersion);

    Date selectUserCreatedAt(@Param("userId") long userId);

    long countVotesByUserSince(@Param("userId") long userId, @Param("since") Date since);

    long countVotesByDeviceSince(@Param("deviceHash") String deviceHash, @Param("since") Date since);

    long countUsersByDeviceSince(@Param("deviceHash") String deviceHash, @Param("since") Date since);

    long countVotesByIpSince(@Param("ipHash") String ipHash, @Param("since") Date since);

    long countUsersByIpSince(@Param("ipHash") String ipHash, @Param("since") Date since);

    TicketRiskAssessmentRow selectAssessment(@Param("userId") long userId,
                                             @Param("clientRequestId") String clientRequestId);

    int insertAssessmentIgnore(@Param("command") com.java2nb.novel.service.gamification.TicketRiskCommand command,
                               @Param("riskScore") int riskScore,
                               @Param("action") String action,
                               @Param("matchedRules") String matchedRules);

    int insertReviewIgnore(@Param("assessmentId") long assessmentId);

    TicketRiskReviewRow lockReview(@Param("assessmentId") long assessmentId);

    TicketRiskReviewRow selectReview(@Param("assessmentId") long assessmentId);

    int updateReview(@Param("assessmentId") long assessmentId,
                     @Param("expectedVersion") long expectedVersion,
                     @Param("decision") String decision,
                     @Param("operatorId") long operatorId,
                     @Param("reason") String reason,
                     @Param("reviewedAt") Date reviewedAt);

    int insertReviewAudit(@Param("assessmentId") long assessmentId,
                          @Param("fromStatus") String fromStatus,
                          @Param("toStatus") String toStatus,
                          @Param("operatorId") long operatorId,
                          @Param("reason") String reason);
}
