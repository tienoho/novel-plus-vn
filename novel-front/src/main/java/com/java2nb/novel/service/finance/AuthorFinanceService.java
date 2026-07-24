package com.java2nb.novel.service.finance;

public interface AuthorFinanceService {

    AuthorKycStatus submitKyc(long authorId, long userId, KycSubmissionRequest request);

    AuthorKycStatus getKycStatus(long authorId);

    AuthorWithdrawalRow requestWithdrawal(long authorId, long userId, WithdrawalRequestInput request);

    long getAvailableRevenue(long authorId);
}
