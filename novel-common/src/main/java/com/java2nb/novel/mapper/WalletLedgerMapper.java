package com.java2nb.novel.mapper;

import com.java2nb.novel.service.wallet.LedgerTransactionRow;
import com.java2nb.novel.service.wallet.WalletAccountRow;
import com.java2nb.novel.service.wallet.WalletHistoryItem;
import com.java2nb.novel.service.wallet.WalletEntryRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WalletLedgerMapper {

    int insertWalletAccount(@Param("ownerType") String ownerType, @Param("ownerId") long ownerId,
                            @Param("accountType") String accountType);

    WalletAccountRow selectWalletAccount(@Param("ownerType") String ownerType, @Param("ownerId") long ownerId,
                                         @Param("accountType") String accountType);

    List<WalletAccountRow> lockWalletAccounts(@Param("walletIds") List<Long> walletIds);

    int insertTransaction(@Param("transactionNo") String transactionNo,
                          @Param("idempotencyKey") String idempotencyKey,
                          @Param("requestHash") String requestHash,
                          @Param("businessType") String businessType,
                          @Param("businessId") String businessId,
                          @Param("totalAmount") long totalAmount,
                          @Param("reversalOfTransactionId") Long reversalOfTransactionId,
                          @Param("description") String description);

    LedgerTransactionRow selectTransactionByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    List<WalletEntryRow> selectEntriesByTransactionId(@Param("transactionId") long transactionId);

    int updateWalletBalance(@Param("walletId") long walletId, @Param("expectedVersion") long expectedVersion,
                            @Param("amount") long amount);

    int updateWalletBalanceAllowReaderDebt(@Param("walletId") long walletId,
                                           @Param("expectedVersion") long expectedVersion,
                                           @Param("amount") long amount);

    int insertEntry(@Param("transactionId") long transactionId, @Param("walletId") long walletId,
                    @Param("amount") long amount, @Param("balanceAfter") long balanceAfter);

    int syncUserBalance(@Param("userId") long userId, @Param("balance") long balance);

    long countReaderHistory(@Param("userId") long userId);

    List<WalletHistoryItem> selectReaderHistory(@Param("userId") long userId, @Param("offset") long offset,
                                                @Param("limit") int limit);

    Long selectAuthorAvailableBalance(@Param("authorId") long authorId);

    List<java.util.Map<String, Object>> checkZeroSumLedger();

    List<java.util.Map<String, Object>> checkProjectionMismatch();
}
