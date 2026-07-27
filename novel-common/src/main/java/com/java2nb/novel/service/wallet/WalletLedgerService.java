package com.java2nb.novel.service.wallet;

public interface WalletLedgerService {

    WalletPostResult creditReaderTopUp(long userId, long amount, String businessId, String idempotencyKey);

    WalletPostResult purchaseChapter(long userId, long authorId, long amount, long authorAmount,
                                     String businessId, String idempotencyKey);

    WalletPostResult creditReaderReward(long userId, long amount, String businessId, String idempotencyKey,
                                        String description);

    WalletPostResult reverseTransaction(String originalIdempotencyKey, String businessType, String businessId,
                                        String idempotencyKey, String description);

    WalletPostResult holdReaderRefund(long userId, long amount, String refundNo, String idempotencyKey);

    WalletPostResult settleReaderRefund(long amount, String refundNo, String idempotencyKey);

    WalletPostResult releaseReaderRefund(long userId, long amount, String refundNo, String idempotencyKey);

    WalletPostResult chargebackReaderTopUp(long userId, String originalIdempotencyKey, String chargebackNo,
                                           String idempotencyKey, String description);

    WalletPostResult holdAuthorWithdrawal(long authorId, long amount, String withdrawalNo, String idempotencyKey);

    WalletPostResult settleAuthorWithdrawal(long amount, String withdrawalNo, String idempotencyKey);

    long getAuthorAvailableBalance(long authorId);

    WalletHistoryPage listReaderHistory(long userId, int page, int pageSize);
}
