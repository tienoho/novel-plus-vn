package com.java2nb.novel.dao;

import com.java2nb.common.annotation.SanitizeMap;
import com.java2nb.novel.domain.AuthorKycReviewDO;
import com.java2nb.novel.domain.AuthorWithdrawalReviewDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AuthorFinanceReviewDao {

    List<AuthorKycReviewDO> listKyc(@SanitizeMap Map<String, Object> params);
    int countKyc(Map<String, Object> params);
    AuthorKycReviewDO getKyc(@Param("id") long id);
    int reviewKyc(@Param("id") long id, @Param("expectedVersion") int expectedVersion,
                  @Param("toStatus") String toStatus, @Param("reviewerId") long reviewerId,
                  @Param("reason") String reason);
    int insertKycAudit(@Param("profile") AuthorKycReviewDO profile, @Param("eventType") String eventType,
                       @Param("toStatus") String toStatus, @Param("actorId") long actorId,
                       @Param("reason") String reason);

    List<AuthorWithdrawalReviewDO> listWithdrawals(@SanitizeMap Map<String, Object> params);
    int countWithdrawals(Map<String, Object> params);
    AuthorWithdrawalReviewDO getWithdrawal(@Param("id") long id);
    int approveWithdrawal(@Param("id") long id, @Param("expectedVersion") long expectedVersion,
                          @Param("withheldTaxVnd") long withheldTaxVnd, @Param("netAmountVnd") long netAmountVnd,
                          @Param("reviewerId") long reviewerId);
    int requestWithdrawalRelease(@Param("id") long id, @Param("expectedStatus") String expectedStatus,
                                 @Param("expectedVersion") long expectedVersion,
                                 @Param("targetStatus") String targetStatus,
                                 @Param("reviewerId") long reviewerId, @Param("reason") String reason);
    int markWithdrawalProcessing(@Param("id") long id, @Param("expectedVersion") long expectedVersion,
                                 @Param("executorId") long executorId);
    int requestWithdrawalSettlement(@Param("id") long id, @Param("expectedVersion") long expectedVersion,
                                    @Param("executorId") long executorId,
                                    @Param("providerReference") String providerReference);
    int insertWithdrawalAudit(@Param("withdrawal") AuthorWithdrawalReviewDO withdrawal,
                              @Param("eventType") String eventType, @Param("toStatus") String toStatus,
                              @Param("actorId") long actorId, @Param("reason") String reason);
}
