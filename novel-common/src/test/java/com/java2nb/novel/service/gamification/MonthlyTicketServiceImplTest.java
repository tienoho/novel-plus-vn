package com.java2nb.novel.service.gamification;

import com.java2nb.novel.mapper.MonthlyTicketMapper;
import com.java2nb.novel.service.impl.MonthlyTicketServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.InOrder;

class MonthlyTicketServiceImplTest {

    private static final long USER_ID = 4_401L;
    private static final Date EFFECTIVE_AT = Date.from(Instant.parse("2026-08-01T00:00:00Z"));
    private static final Date EXPIRE_AT =
        Date.from(Instant.parse("2026-08-01T00:00:00Z").plus(60, ChronoUnit.DAYS));

    private MonthlyTicketMapper mapper;
    private MonthlyTicketServiceImpl service;
    private Map<String, TicketLedgerRow> ledgerByKey;
    private TicketAccountRow account;

    @BeforeEach
    void setUp() {
        mapper = mock(MonthlyTicketMapper.class);
        service = new MonthlyTicketServiceImpl(mapper);
        ledgerByKey = new HashMap<>();

        account = new TicketAccountRow();
        account.setId(77L);
        account.setUserId(USER_ID);
        account.setAvailableBalance(0L);
        account.setStatus("ACTIVE");
        account.setVersion(0L);

        when(mapper.selectLedgerByIdempotencyKey(anyString()))
            .thenAnswer(invocation -> ledgerByKey.get(invocation.getArgument(0, String.class)));
        when(mapper.lockAccountByUserId(USER_ID)).thenReturn(account);
        when(mapper.selectAccount(USER_ID)).thenReturn(account);
        when(mapper.insertLot(anyLong(), anyString(), anyString(), anyLong(), anyLong(), any(), any(),
            anyString())).thenReturn(1);
        when(mapper.creditAccount(anyLong(), anyLong(), anyLong())).thenReturn(1);
        when(mapper.insertLedger(anyString(), anyLong(), anyString(), anyLong(), anyLong(), anyString(),
            anyString(), anyString(), anyString(), any(), any(), any(), anyString(), any(), any(),
            anyString()))
            .thenAnswer(invocation -> {
                String key = invocation.getArgument(7, String.class);
                TicketLedgerRow row = new TicketLedgerRow();
                row.setId(901L);
                row.setUserId(invocation.getArgument(1, Long.class));
                row.setEntryType(invocation.getArgument(2, String.class));
                row.setAmount(invocation.getArgument(3, Long.class));
                row.setBalanceAfter(invocation.getArgument(4, Long.class));
                row.setBusinessType(invocation.getArgument(5, String.class));
                row.setBusinessId(invocation.getArgument(6, String.class));
                row.setIdempotencyKey(key);
                row.setRequestHash(invocation.getArgument(8, String.class));
                row.setOperatorType(invocation.getArgument(12, String.class));
                row.setOperatorId(invocation.getArgument(13, Long.class));
                row.setReason(invocation.getArgument(14, String.class));
                row.setPolicyVersion(invocation.getArgument(15, String.class));
                ledgerByKey.put(key, row);
                return 1;
            });
    }

    private TicketGrantCommand grantOf(long amount, String idempotencyKey) {
        return new TicketGrantCommand(USER_ID, amount, "CHECK_IN", "2026-08-01", idempotencyKey,
            EFFECTIVE_AT, EXPIRE_AT, "SYSTEM", null, null, "v1");
    }

    @Test
    void grantsOneLotAndCreditsTheProjection() {
        assertThat(service.grant(grantOf(5L, "CHECKIN:4401:2026-08-01")))
            .isEqualTo(TicketPostResult.POSTED);

        verify(mapper).insertLot(eq(USER_ID), eq("CHECK_IN"), eq("2026-08-01"), eq(5L), eq(901L),
            eq(EFFECTIVE_AT), eq(EXPIRE_AT), eq("v1"));
        verify(mapper).creditAccount(77L, 0L, 5L);
    }

    @Test
    void replayingTheSameGrantDoesNotCreateASecondLot() {
        String key = "CHECKIN:4401:2026-08-01";
        assertThat(service.grant(grantOf(5L, key))).isEqualTo(TicketPostResult.POSTED);

        assertThat(service.grant(grantOf(5L, key))).isEqualTo(TicketPostResult.ALREADY_POSTED);

        // Đúng một lô và đúng một lần cộng số dư, dù đã gọi hai lần.
        verify(mapper).insertLot(anyLong(), anyString(), anyString(), anyLong(), anyLong(), any(),
            any(), anyString());
        verify(mapper).creditAccount(anyLong(), anyLong(), anyLong());
    }

    @Test
    void reusingAKeyForADifferentAmountIsRejected() {
        String key = "CHECKIN:4401:2026-08-01";
        service.grant(grantOf(5L, key));

        assertThatThrownBy(() -> service.grant(grantOf(50L, key)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Khóa idempotency");
    }

    @Test
    void concurrentInsertLosingTheUniqueKeyRaceReturnsTheExistingEntry() {
        String key = "ADMIN_GRANT:batch-9:4401";
        // Mô phỏng một giao dịch song song commit ngay sau đường nhanh: INSERT ném lỗi trùng khoá,
        // và lúc đọc lại thì bản ghi của giao dịch kia đã hiện diện.
        TicketGrantCommand command = new TicketGrantCommand(USER_ID, 3L, "ADMIN_GRANT", "batch-9",
            key, EFFECTIVE_AT, EXPIRE_AT, "ADMIN", 12L, "bù sự cố", "v1");
        TicketLedgerRow winner = new TicketLedgerRow();
        winner.setId(902L);
        winner.setUserId(USER_ID);
        winner.setEntryType("GRANT");
        winner.setAmount(3L);
        winner.setBusinessType("ADMIN_GRANT");
        winner.setBusinessId("batch-9");
        winner.setIdempotencyKey(key);
        winner.setOperatorType("ADMIN");
        winner.setOperatorId(12L);
        winner.setReason("bù sự cố");
        winner.setPolicyVersion("v1");
        when(mapper.insertLedger(anyString(), anyLong(), anyString(), anyLong(), anyLong(), anyString(),
            anyString(), eq(key), anyString(), any(), any(), any(), anyString(), any(), any(),
            anyString()))
            .thenAnswer(invocation -> {
                winner.setRequestHash(invocation.getArgument(8, String.class));
                ledgerByKey.put(key, winner);
                throw new DuplicateKeyException("uk_mt_ledger_idempotency");
            });

        assertThat(service.grant(command)).isEqualTo(TicketPostResult.ALREADY_POSTED);

        verify(mapper, never()).insertLot(anyLong(), anyString(), anyString(), anyLong(), anyLong(),
            any(), any(), anyString());
        verify(mapper, never()).creditAccount(anyLong(), anyLong(), anyLong());
    }

    @Test
    void concurrentProjectionUpdateAbortsTheGrant() {
        when(mapper.creditAccount(anyLong(), anyLong(), anyLong())).thenReturn(0);

        assertThatThrownBy(() -> service.grant(grantOf(5L, "CHECKIN:4401:2026-08-02")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("cập nhật đồng thời");
    }

    @Test
    void frozenAccountCannotReceiveTickets() {
        account.setStatus("FROZEN");

        assertThatThrownBy(() -> service.grant(grantOf(5L, "CHECKIN:4401:2026-08-03")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("đóng băng");
    }

    @Test
    void balanceAfterOnTheEntryReflectsTheCreditedTotal() {
        account.setAvailableBalance(12L);

        service.grant(grantOf(8L, "CHECKIN:4401:2026-08-04"));

        assertThat(ledgerByKey.get("CHECKIN:4401:2026-08-04").getBalanceAfter()).isEqualTo(20L);
    }

    @Test
    void grantCommandRejectsAdminOperationsWithoutAccountability() {
        assertThatThrownBy(() -> new TicketGrantCommand(USER_ID, 1L, "ADMIN_GRANT", "batch-1",
            "ADMIN_GRANT:batch-1:4401", EFFECTIVE_AT, EXPIRE_AT, "ADMIN", null, null, "v1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("người thực hiện và lý do");
    }

    @Test
    void grantCommandRejectsAnInvalidValidityWindow() {
        assertThatThrownBy(() -> new TicketGrantCommand(USER_ID, 1L, "CHECK_IN", "2026-08-01",
            "CHECKIN:4401:2026-08-01", EXPIRE_AT, EFFECTIVE_AT, "SYSTEM", null, null, "v1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Khoảng hiệu lực");
    }

    @Test
    void reusingAKeyWithDifferentAuditDetailsIsRejected() {
        String key = "ADMIN_GRANT:batch-10:4401";
        TicketGrantCommand original = new TicketGrantCommand(USER_ID, 3L, "ADMIN_GRANT", "batch-10",
            key, EFFECTIVE_AT, EXPIRE_AT, "ADMIN", 12L, "bù sự cố", "v1");
        TicketGrantCommand changedReason = new TicketGrantCommand(USER_ID, 3L, "ADMIN_GRANT", "batch-10",
            key, EFFECTIVE_AT, EXPIRE_AT, "ADMIN", 12L, "thay lý do", "v1");

        assertThat(service.grant(original)).isEqualTo(TicketPostResult.POSTED);
        assertThatThrownBy(() -> service.grant(changedReason))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Khóa idempotency");
    }

    @Test
    void grantCommandDefensivelyCopiesMutableDates() {
        Date mutableEffectiveAt = new Date(EFFECTIVE_AT.getTime());
        Date mutableExpireAt = new Date(EXPIRE_AT.getTime());
        TicketGrantCommand command = new TicketGrantCommand(USER_ID, 1L, "CHECK_IN", "2026-08-01",
            "CHECKIN:4401:2026-08-05", mutableEffectiveAt, mutableExpireAt,
            "SYSTEM", null, null, "v1");

        mutableEffectiveAt.setTime(0L);
        mutableExpireAt.setTime(1L);
        Date returnedEffectiveAt = command.effectiveAt();
        returnedEffectiveAt.setTime(2L);

        assertThat(command.effectiveAt()).isEqualTo(EFFECTIVE_AT);
        assertThat(command.expireAt()).isEqualTo(EXPIRE_AT);
    }

    @Test
    void historyPageSizeIsClampedToProtectTheDatabase() {
        when(mapper.countLedgerByUser(USER_ID)).thenReturn(5L);

        TicketHistoryPage page = service.listHistory(USER_ID, 0, 5_000);

        assertThat(page.page()).isEqualTo(1);
        assertThat(page.pageSize()).isEqualTo(100);
        verify(mapper).selectLedgerByUser(USER_ID, 0L, 100);
    }

    @Test
    void expiresDueLotsAndDebitsProjectionInOneUserTransaction() {
        account.setAvailableBalance(7L);
        TicketLotRow first = expiredLot(301L, 2L, 4L);
        TicketLotRow second = expiredLot(302L, 3L, 6L);
        when(mapper.lockExpiredLotsByUser(USER_ID, EFFECTIVE_AT)).thenReturn(List.of(first, second));
        when(mapper.expireLot(anyLong(), anyLong(), anyLong(), eq(EFFECTIVE_AT))).thenReturn(1);
        when(mapper.insertLotAllocation(anyLong(), anyLong(), anyLong(), anyLong())).thenReturn(1);
        when(mapper.expireFromAccount(77L, 0L, 5L)).thenReturn(1);

        TicketExpiryResult result = service.expireDueLots(
            USER_ID, EFFECTIVE_AT, "2026-08-01", "v1");

        assertThat(result).isEqualTo(new TicketExpiryResult(2, 5));
        verify(mapper).insertLotAllocation(901L, 301L, 2L, 0L);
        verify(mapper).insertLotAllocation(901L, 302L, 3L, 0L);
        verify(mapper).expireFromAccount(77L, 0L, 5L);
        InOrder lockOrder = inOrder(mapper);
        lockOrder.verify(mapper).lockAccountByUserId(USER_ID);
        lockOrder.verify(mapper).lockExpiredLotsByUser(USER_ID, EFFECTIVE_AT);
    }

    @Test
    void expiryStopsWhenProjectionCannotCoverExpiredLots() {
        account.setAvailableBalance(1L);
        when(mapper.lockExpiredLotsByUser(USER_ID, EFFECTIVE_AT))
            .thenReturn(List.of(expiredLot(301L, 2L, 4L)));

        assertThatThrownBy(() -> service.expireDueLots(
            USER_ID, EFFECTIVE_AT, "2026-08-01", "v1"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("thấp hơn");

        verify(mapper, never()).insertLedger(anyString(), anyLong(), anyString(), anyLong(), anyLong(),
            anyString(), anyString(), anyString(), anyString(), any(), any(), any(), anyString(),
            any(), any(), anyString());
    }

    @Test
    void expiryReturnsEmptyWhenUserHasNoDueLots() {
        when(mapper.lockExpiredLotsByUser(USER_ID, EFFECTIVE_AT)).thenReturn(List.of());

        assertThat(service.expireDueLots(USER_ID, EFFECTIVE_AT, "2026-08-01", "v1"))
            .isEqualTo(TicketExpiryResult.EMPTY);

        verify(mapper, never()).expireFromAccount(anyLong(), anyLong(), anyLong());
    }

    @Test
    void tickerHidesEntriesWithFlaggedNicknamesAndCapsAtLimit() {
        com.java2nb.novel.core.utils.SensitiveWordFilter.getInstance().addWord("tuquantam0042");
        TickerEntryRow clean = tickerEntry("DocGiaChanChinh", "Truyện A", 3);
        TickerEntryRow flagged = tickerEntry("Kehang tuquantam0042", "Truyện B", 5);
        TickerEntryRow blankNickname = tickerEntry("   ", "Truyện C", 1);
        when(mapper.selectRecentTickerEntries(40))
            .thenReturn(List.of(clean, flagged, blankNickname));

        List<TickerEntryRow> result = service.listTicker(20);

        assertThat(result).containsExactly(clean);
    }

    @Test
    void tickerClampsLimitToConfiguredMaximum() {
        when(mapper.selectRecentTickerEntries(anyInt())).thenReturn(List.of());

        service.listTicker(500);

        verify(mapper).selectRecentTickerEntries(100);
    }

    private TickerEntryRow tickerEntry(String nickname, String bookName, long ticketCount) {
        TickerEntryRow row = new TickerEntryRow();
        row.setNickname(nickname);
        row.setBookName(bookName);
        row.setTicketCount(ticketCount);
        return row;
    }

    private TicketLotRow expiredLot(long id, long remainingAmount, long version) {
        TicketLotRow lot = new TicketLotRow();
        lot.setId(id);
        lot.setUserId(USER_ID);
        lot.setRemainingAmount(remainingAmount);
        lot.setVersion(version);
        return lot;
    }
}
