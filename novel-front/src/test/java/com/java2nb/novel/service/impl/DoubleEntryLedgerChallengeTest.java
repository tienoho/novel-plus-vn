package com.java2nb.novel.service.impl;

import com.java2nb.novel.mapper.WalletLedgerMapper;
import com.java2nb.novel.service.wallet.InsufficientWalletBalanceException;
import com.java2nb.novel.service.wallet.LedgerTransactionRow;
import com.java2nb.novel.service.wallet.WalletAccountRow;
import com.java2nb.novel.service.wallet.WalletEntryRow;
import com.java2nb.novel.service.wallet.WalletPostResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Challenge 1: Double-Entry Ledger Balance Integrity & Reversal Behavior")
class DoubleEntryLedgerChallengeTest {

    private WalletLedgerMapper mapper;
    private WalletLedgerServiceImpl service;
    private Map<String, WalletAccountRow> wallets;

    @BeforeEach
    void setUp() {
        mapper = mock(WalletLedgerMapper.class);
        service = new WalletLedgerServiceImpl(mapper);
        wallets = new HashMap<>();
        when(mapper.insertWalletAccount(anyString(), anyLong(), anyString())).thenReturn(1);
        when(mapper.selectWalletAccount(anyString(), anyLong(), anyString())).thenAnswer(invocation ->
            wallets.get(key(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2))));
        when(mapper.insertTransaction(anyString(), anyString(), anyString(), anyString(), anyString(), anyLong(),
            any(), anyString())).thenReturn(1);
        when(mapper.updateWalletBalance(anyLong(), anyLong(), anyLong())).thenReturn(1);
        when(mapper.updateWalletBalanceAllowReaderDebt(anyLong(), anyLong(), anyLong())).thenReturn(1);
        when(mapper.insertEntry(anyLong(), anyLong(), anyLong(), anyLong())).thenReturn(1);
        when(mapper.syncUserBalance(anyLong(), anyLong())).thenReturn(1);
    }

    @Test
    @DisplayName("Reversal of an already reversed transaction must be rejected")
    void testReversingAReversedTransactionIsRejected() {
        LedgerTransactionRow original = transaction(100L, "hash1", "CHAPTER_PURCHASE", "99", 10L);
        original.setReversalOfTransactionId(50L); // This transaction is ALREADY a reversal

        when(mapper.selectTransactionByIdempotencyKey("REVERSAL_TX:100")).thenReturn(original);

        assertThatThrownBy(() -> service.reverseTransaction("REVERSAL_TX:100", "CHAPTER_REFUND",
            "ref-100", "IDEM_REFUND:100", "Đảo của giao dịch đảo"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Không được đảo một giao dịch đảo");
    }

    @Test
    @DisplayName("Reversal causing negative non-system balance throws InsufficientWalletBalanceException")
    void testReversalCausingNegativeBalanceIsRejected() {
        // Reader has 5 Xu remaining, but reversal attempts to deduct 10 Xu
        WalletAccountRow reader = wallet(1L, "USER", 11L, "READER_XU", 5L, 1L);
        WalletAccountRow author = wallet(2L, "AUTHOR", 22L, "AUTHOR_REVENUE_XU", 7L, 1L);
        WalletAccountRow systemIssuance = wallet(3L, "SYSTEM", 0L, "SYSTEM_ISSUANCE", -10L, 1L);
        addWallet(reader);
        addWallet(author);
        addWallet(systemIssuance);

        LedgerTransactionRow original = transaction(200L, "hash2", "TOP_UP", "tu-1", 10L);
        LedgerTransactionRow reversalTx = transaction(201L, "hash_rev2", "TOP_UP_REFUND", "ref-tu-1", 10L);
        when(mapper.selectTransactionByIdempotencyKey("TOP_UP:11:1")).thenReturn(original);
        when(mapper.selectTransactionByIdempotencyKey("IDEM_REF_TU:1")).thenReturn(null, reversalTx);
        when(mapper.selectEntriesByTransactionId(200L)).thenReturn(List.of(
            entry(1L, "USER", 11L, "READER_XU", 10L), // Original was credit 10 xu
            entry(3L, "SYSTEM", 0L, "SYSTEM_ISSUANCE", -10L)
        ));
        when(mapper.lockWalletAccounts(List.of(1L, 3L))).thenReturn(List.of(reader, systemIssuance));

        // Reversal will try to deduct 10 Xu from reader, but reader balance is 5 -> negative balance -5!
        assertThatThrownBy(() -> service.reverseTransaction("TOP_UP:11:1", "TOP_UP_REFUND",
            "ref-tu-1", "IDEM_REF_TU:1", "Đảo nạp tiền"))
            .isInstanceOf(InsufficientWalletBalanceException.class);
    }

    @Test
    @DisplayName("Reversal containing multiple reader wallets throws IllegalStateException")
    void testReversalWithMultipleReaderWalletsIsRejected() {
        LedgerTransactionRow original = transaction(300L, "hash3", "MULTI_TRANSFER", "m1", 20L);
        when(mapper.selectTransactionByIdempotencyKey("MULTI:1")).thenReturn(original);
        when(mapper.selectEntriesByTransactionId(300L)).thenReturn(List.of(
            entry(1L, "USER", 11L, "READER_XU", -10L),
            entry(2L, "USER", 22L, "READER_XU", 10L) // Second reader wallet in same tx!
        ));

        assertThatThrownBy(() -> service.reverseTransaction("MULTI:1", "MULTI_REFUND",
            "ref-m1", "IDEM_MULTI_REF:1", "Đảo chuyển tiền multi"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Giao dịch gốc chứa nhiều ví độc giả");
    }

    @Test
    @DisplayName("Non-positive transaction amount must be rejected")
    void testNonPositiveAmountRejected() {
        assertThatThrownBy(() -> service.creditReaderTopUp(11L, 0L, "123", "KEY:0"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Số Xu nạp phải lớn hơn 0");

        assertThatThrownBy(() -> service.creditReaderTopUp(11L, -500L, "123", "KEY:-500"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Số Xu nạp phải lớn hơn 0");
    }

    @Test
    @DisplayName("Chargeback bắt buộc được ghi sổ và chuyển ví thiếu Xu sang nợ")
    void chargebackAllowsReaderDebtButKeepsDoubleEntryBalanced() {
        WalletAccountRow reader = wallet(1L, "USER", 11L, "READER_XU", 5L, 1L);
        WalletAccountRow issuance = wallet(2L, "SYSTEM", 0L, "SYSTEM_ISSUANCE", -10L, 1L);
        addWallet(reader);
        addWallet(issuance);

        LedgerTransactionRow original = transaction(400L, "hash4", "TOP_UP", "tu-4", 10L);
        original.setIdempotencyKey("VNPAY_TOP_UP:4");
        LedgerTransactionRow reversal = transaction(401L, "hash5", "CHARGEBACK", "CB-4", 10L);
        when(mapper.selectTransactionByIdempotencyKey("VNPAY_TOP_UP:4")).thenReturn(original);
        when(mapper.selectTransactionByIdempotencyKey("CHARGEBACK_REVERSAL:4")).thenReturn(null, reversal);
        when(mapper.selectEntriesByTransactionId(400L)).thenReturn(List.of(
            entry(1L, "USER", 11L, "READER_XU", 10L),
            entry(2L, "SYSTEM", 0L, "SYSTEM_ISSUANCE", -10L)
        ));
        when(mapper.lockWalletAccounts(List.of(1L, 2L))).thenReturn(List.of(reader, issuance));

        WalletPostResult result = service.chargebackReaderTopUp(11L, "VNPAY_TOP_UP:4", "CB-4",
            "CHARGEBACK_REVERSAL:4", "Chargeback ngân hàng");

        assertThat(result).isEqualTo(WalletPostResult.POSTED);
        verify(mapper).updateWalletBalanceAllowReaderDebt(1L, 1L, -10L);
        verify(mapper).updateWalletBalance(2L, 1L, 10L);
        verify(mapper).insertEntry(401L, 1L, -10L, -5L);
        verify(mapper).insertEntry(401L, 2L, 10L, 0L);
        verify(mapper).syncUserBalance(11L, -5L);
    }

    private void addWallet(WalletAccountRow wallet) {
        wallets.put(key(wallet.getOwnerType(), wallet.getOwnerId(), wallet.getAccountType()), wallet);
    }

    private String key(String ownerType, long ownerId, String accountType) {
        return ownerType + ':' + ownerId + ':' + accountType;
    }

    private WalletAccountRow wallet(long id, String ownerType, long ownerId, String accountType, long balance,
                                    long version) {
        WalletAccountRow wallet = new WalletAccountRow();
        wallet.setId(id);
        wallet.setOwnerType(ownerType);
        wallet.setOwnerId(ownerId);
        wallet.setAccountType(accountType);
        wallet.setAvailableBalance(balance);
        wallet.setVersion(version);
        return wallet;
    }

    private LedgerTransactionRow transaction(long id, String requestHash, String businessType, String businessId,
                                             long totalAmount) {
        LedgerTransactionRow transaction = new LedgerTransactionRow();
        transaction.setId(id);
        transaction.setRequestHash(requestHash);
        transaction.setBusinessType(businessType);
        transaction.setBusinessId(businessId);
        transaction.setTotalAmount(totalAmount);
        return transaction;
    }

    private WalletEntryRow entry(long walletId, String ownerType, long ownerId, String accountType, long amount) {
        WalletEntryRow entry = new WalletEntryRow();
        entry.setWalletAccountId(walletId);
        entry.setOwnerType(ownerType);
        entry.setOwnerId(ownerId);
        entry.setAccountType(accountType);
        entry.setAmount(amount);
        return entry;
    }
}
