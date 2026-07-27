package com.java2nb.novel.service.impl;

import com.java2nb.novel.mapper.WalletLedgerMapper;
import com.java2nb.novel.service.wallet.InsufficientWalletBalanceException;
import com.java2nb.novel.service.wallet.LedgerTransactionRow;
import com.java2nb.novel.service.wallet.WalletAccountRow;
import com.java2nb.novel.service.wallet.WalletEntryRow;
import com.java2nb.novel.service.wallet.WalletHistoryItem;
import com.java2nb.novel.service.wallet.WalletPostResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WalletLedgerServiceImplTest {

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
        when(mapper.insertEntry(anyLong(), anyLong(), anyLong(), anyLong())).thenReturn(1);
        when(mapper.syncUserBalance(anyLong(), anyLong())).thenReturn(1);
    }

    @Test
    void postsBalancedTopUpAndTreatsRetryAsIdempotent() {
        WalletAccountRow issuance = wallet(1L, "SYSTEM", 0L, "SYSTEM_ISSUANCE", -500L, 2L);
        WalletAccountRow reader = wallet(2L, "USER", 11L, "READER_XU", 100L, 3L);
        addWallet(issuance);
        addWallet(reader);
        LedgerTransactionRow posted = transaction(50L, null, "TOP_UP", "123", 1_000L);
        when(mapper.selectTransactionByIdempotencyKey("VNPAY_TOP_UP:123")).thenReturn(null, posted);
        when(mapper.lockWalletAccounts(List.of(1L, 2L))).thenReturn(List.of(issuance, reader));

        assertThat(service.creditReaderTopUp(11L, 1_000L, "123", "VNPAY_TOP_UP:123"))
            .isEqualTo(WalletPostResult.POSTED);

        ArgumentCaptor<String> requestHash = ArgumentCaptor.forClass(String.class);
        verify(mapper).insertTransaction(anyString(), eq("VNPAY_TOP_UP:123"), requestHash.capture(),
            eq("TOP_UP"), eq("123"), eq(1_000L), eq(null), anyString());
        posted.setRequestHash(requestHash.getValue());
        when(mapper.selectTransactionByIdempotencyKey("VNPAY_TOP_UP:123")).thenReturn(posted);

        assertThat(service.creditReaderTopUp(11L, 1_000L, "123", "VNPAY_TOP_UP:123"))
            .isEqualTo(WalletPostResult.ALREADY_POSTED);

        verify(mapper, times(1)).insertTransaction(anyString(), anyString(), anyString(), anyString(), anyString(),
            anyLong(), any(), anyString());
        verify(mapper).insertEntry(50L, 1L, -1_000L, -1_500L);
        verify(mapper).insertEntry(50L, 2L, 1_000L, 1_100L);
        verify(mapper).syncUserBalance(11L, 1_100L);
    }

    @Test
    void topUpReducesReaderDebtBeforeRestoringActiveBalance() {
        WalletAccountRow issuance = wallet(1L, "SYSTEM", 0L, "SYSTEM_ISSUANCE", -1_000L, 2L);
        WalletAccountRow reader = wallet(2L, "USER", 11L, "READER_XU", -400L, 3L);
        addWallet(issuance);
        addWallet(reader);
        when(mapper.selectTransactionByIdempotencyKey("VNPAY_TOP_UP:DEBT"))
            .thenReturn(null, transaction(51L, null, "TOP_UP", "DEBT", 200L));
        when(mapper.lockWalletAccounts(List.of(1L, 2L))).thenReturn(List.of(issuance, reader));
        when(mapper.updateWalletBalanceAllowReaderDebt(2L, 3L, 200L)).thenReturn(1);

        assertThat(service.creditReaderTopUp(11L, 200L, "DEBT", "VNPAY_TOP_UP:DEBT"))
            .isEqualTo(WalletPostResult.POSTED);

        verify(mapper).insertEntry(51L, 1L, -200L, -1_200L);
        verify(mapper).insertEntry(51L, 2L, 200L, -200L);
        verify(mapper).syncUserBalance(11L, -200L);
    }

    @Test
    void splitsPurchaseBetweenAuthorAndPlatform() {
        WalletAccountRow reader = wallet(1L, "USER", 11L, "READER_XU", 100L, 0L);
        WalletAccountRow author = wallet(2L, "AUTHOR", 22L, "AUTHOR_REVENUE_XU", 5L, 0L);
        WalletAccountRow platform = wallet(3L, "SYSTEM", 0L, "PLATFORM_REVENUE", 7L, 0L);
        addWallet(reader);
        addWallet(author);
        addWallet(platform);
        when(mapper.selectTransactionByIdempotencyKey("CHAPTER_PURCHASE:11:99"))
            .thenReturn(null, transaction(60L, null, "CHAPTER_PURCHASE", "99", 10L));
        when(mapper.lockWalletAccounts(List.of(1L, 2L, 3L))).thenReturn(List.of(reader, author, platform));

        assertThat(service.purchaseChapter(11L, 22L, 10L, 7L, "99", "CHAPTER_PURCHASE:11:99"))
            .isEqualTo(WalletPostResult.POSTED);

        verify(mapper).insertEntry(60L, 1L, -10L, 90L);
        verify(mapper).insertEntry(60L, 2L, 7L, 12L);
        verify(mapper).insertEntry(60L, 3L, 3L, 10L);
        verify(mapper).syncUserBalance(11L, 90L);
    }

    @Test
    void rejectsPurchaseWhenReaderWalletIsInsufficient() {
        WalletAccountRow reader = wallet(1L, "USER", 11L, "READER_XU", 5L, 0L);
        WalletAccountRow author = wallet(2L, "AUTHOR", 22L, "AUTHOR_REVENUE_XU", 0L, 0L);
        WalletAccountRow platform = wallet(3L, "SYSTEM", 0L, "PLATFORM_REVENUE", 0L, 0L);
        addWallet(reader);
        addWallet(author);
        addWallet(platform);
        when(mapper.selectTransactionByIdempotencyKey("CHAPTER_PURCHASE:11:99"))
            .thenReturn(null, transaction(60L, null, "CHAPTER_PURCHASE", "99", 10L));
        when(mapper.lockWalletAccounts(List.of(1L, 2L, 3L))).thenReturn(List.of(reader, author, platform));

        assertThatThrownBy(() -> service.purchaseChapter(11L, 22L, 10L, 7L, "99",
            "CHAPTER_PURCHASE:11:99"))
            .isInstanceOf(InsufficientWalletBalanceException.class);

        verify(mapper, never()).insertEntry(anyLong(), anyLong(), anyLong(), anyLong());
        verify(mapper, never()).syncUserBalance(anyLong(), anyLong());
    }

    @Test
    void rejectsReusedIdempotencyKeyWithDifferentPayload() {
        LedgerTransactionRow existing = transaction(50L, "different-hash", "TOP_UP", "123", 1_000L);
        when(mapper.selectTransactionByIdempotencyKey("VNPAY_TOP_UP:123")).thenReturn(existing);

        assertThatThrownBy(() -> service.creditReaderTopUp(11L, 1_000L, "123", "VNPAY_TOP_UP:123"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("idempotency");

        verify(mapper, never()).insertTransaction(anyString(), anyString(), anyString(), anyString(), anyString(),
            anyLong(), any(), anyString());
    }

    @Test
    void reversesOriginalEntriesExactlyOnce() {
        WalletAccountRow reader = wallet(1L, "USER", 11L, "READER_XU", 90L, 1L);
        WalletAccountRow author = wallet(2L, "AUTHOR", 22L, "AUTHOR_REVENUE_XU", 7L, 1L);
        WalletAccountRow platform = wallet(3L, "SYSTEM", 0L, "PLATFORM_REVENUE", 3L, 1L);
        addWallet(reader);
        addWallet(author);
        addWallet(platform);
        LedgerTransactionRow original = transaction(70L, "original-hash", "CHAPTER_PURCHASE", "99", 10L);
        LedgerTransactionRow reversal = transaction(71L, null, "CHAPTER_REFUND", "refund-99", 10L);
        reversal.setReversalOfTransactionId(70L);
        when(mapper.selectTransactionByIdempotencyKey("CHAPTER_PURCHASE:11:99")).thenReturn(original);
        when(mapper.selectTransactionByIdempotencyKey("CHAPTER_REFUND:99")).thenReturn(null, reversal);
        when(mapper.selectEntriesByTransactionId(70L)).thenReturn(List.of(
            entry(1L, "USER", 11L, "READER_XU", -10L),
            entry(2L, "AUTHOR", 22L, "AUTHOR_REVENUE_XU", 7L),
            entry(3L, "SYSTEM", 0L, "PLATFORM_REVENUE", 3L)
        ));
        when(mapper.lockWalletAccounts(List.of(1L, 2L, 3L))).thenReturn(List.of(reader, author, platform));

        assertThat(service.reverseTransaction("CHAPTER_PURCHASE:11:99", "CHAPTER_REFUND", "refund-99",
            "CHAPTER_REFUND:99", "Hoàn Xu mua chương"))
            .isEqualTo(WalletPostResult.POSTED);

        ArgumentCaptor<String> requestHash = ArgumentCaptor.forClass(String.class);
        verify(mapper).insertTransaction(anyString(), eq("CHAPTER_REFUND:99"), requestHash.capture(),
            eq("CHAPTER_REFUND"), eq("refund-99"), eq(10L), eq(70L), anyString());
        verify(mapper).insertEntry(71L, 1L, 10L, 100L);
        verify(mapper).insertEntry(71L, 2L, -7L, 0L);
        verify(mapper).insertEntry(71L, 3L, -3L, 0L);
        verify(mapper).syncUserBalance(11L, 100L);

        reversal.setRequestHash(requestHash.getValue());
        when(mapper.selectTransactionByIdempotencyKey("CHAPTER_REFUND:99")).thenReturn(reversal);
        assertThat(service.reverseTransaction("CHAPTER_PURCHASE:11:99", "CHAPTER_REFUND", "refund-99",
            "CHAPTER_REFUND:99", "Hoàn Xu mua chương"))
            .isEqualTo(WalletPostResult.ALREADY_POSTED);
        verify(mapper, times(1)).insertTransaction(anyString(), eq("CHAPTER_REFUND:99"), anyString(),
            anyString(), anyString(), anyLong(), any(), anyString());
    }

    @Test
    void returnsBoundedReaderHistoryPage() {
        WalletHistoryItem item = new WalletHistoryItem();
        item.setBusinessType("TOP_UP");
        when(mapper.countReaderHistory(11L)).thenReturn(1L);
        when(mapper.selectReaderHistory(11L, 0L, 100)).thenReturn(List.of(item));

        var page = service.listReaderHistory(11L, 0, 500);

        assertThat(page.page()).isEqualTo(1);
        assertThat(page.pageSize()).isEqualTo(100);
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).containsExactly(item);
    }

    @Test
    void holdsAuthorRevenueWithoutTouchingReaderProjection() {
        WalletAccountRow author = wallet(1L, "AUTHOR", 22L, "AUTHOR_REVENUE_XU", 500L, 0L);
        WalletAccountRow clearing = wallet(2L, "SYSTEM", 0L, "PAYOUT_CLEARING", 0L, 0L);
        addWallet(author);
        addWallet(clearing);
        when(mapper.selectTransactionByIdempotencyKey("AUTHOR_WITHDRAWAL_HOLD:WD-1"))
            .thenReturn(null, transaction(80L, null, "AUTHOR_WITHDRAWAL_HOLD", "WD-1", 200L));
        when(mapper.lockWalletAccounts(List.of(1L, 2L))).thenReturn(List.of(author, clearing));

        assertThat(service.holdAuthorWithdrawal(22L, 200L, "WD-1", "AUTHOR_WITHDRAWAL_HOLD:WD-1"))
            .isEqualTo(WalletPostResult.POSTED);

        verify(mapper).insertEntry(80L, 1L, -200L, 300L);
        verify(mapper).insertEntry(80L, 2L, 200L, 200L);
        verify(mapper, never()).syncUserBalance(anyLong(), anyLong());
    }

    @Test
    void settlesPayoutClearingAgainstIssuanceAccount() {
        WalletAccountRow issuance = wallet(1L, "SYSTEM", 0L, "SYSTEM_ISSUANCE", -1_000L, 2L);
        WalletAccountRow clearing = wallet(2L, "SYSTEM", 0L, "PAYOUT_CLEARING", 200L, 1L);
        addWallet(issuance);
        addWallet(clearing);
        when(mapper.selectTransactionByIdempotencyKey("AUTHOR_WITHDRAWAL_SETTLED:WD-1"))
            .thenReturn(null, transaction(90L, null, "AUTHOR_WITHDRAWAL_SETTLED", "WD-1", 200L));
        when(mapper.lockWalletAccounts(List.of(1L, 2L))).thenReturn(List.of(issuance, clearing));

        assertThat(service.settleAuthorWithdrawal(200L, "WD-1", "AUTHOR_WITHDRAWAL_SETTLED:WD-1"))
            .isEqualTo(WalletPostResult.POSTED);

        verify(mapper).insertEntry(90L, 1L, 200L, -800L);
        verify(mapper).insertEntry(90L, 2L, -200L, 0L);
        verify(mapper, never()).syncUserBalance(anyLong(), anyLong());
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
