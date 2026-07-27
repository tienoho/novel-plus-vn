package com.java2nb.novel.service;

import com.java2nb.novel.domain.AuthorKycReviewDO;
import com.java2nb.novel.domain.AuthorWithdrawalReviewDO;

import java.util.List;
import java.util.Map;

public interface AuthorFinanceReviewService {

    List<AuthorKycReviewDO> listKyc(Map<String, Object> params);
    int countKyc(Map<String, Object> params);
    AuthorKycReviewDO getKycDetail(long id, long actorId);
    void approveKyc(long id, int expectedVersion, long actorId);
    void rejectKyc(long id, int expectedVersion, String reason, long actorId);

    List<AuthorWithdrawalReviewDO> listWithdrawals(Map<String, Object> params);
    int countWithdrawals(Map<String, Object> params);
    AuthorWithdrawalReviewDO getWithdrawal(long id);
    AuthorWithdrawalReviewDO getWithdrawalPayoutDetail(long id, long actorId);
    void approveWithdrawal(long id, long expectedVersion, long withheldTaxVnd, long actorId);
    void rejectWithdrawal(long id, long expectedVersion, String reason, long actorId);
    void markProcessing(long id, long expectedVersion, long actorId);
    void markPaid(long id, long expectedVersion, String providerReference, long actorId);
    void markFailed(long id, long expectedVersion, String reason, long actorId);
    void executeAutoPayout(long id, long actorId);
}
