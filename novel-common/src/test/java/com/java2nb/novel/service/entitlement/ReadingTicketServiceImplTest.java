package com.java2nb.novel.service.entitlement;

import com.java2nb.novel.mapper.ReadingTicketMapper;
import com.java2nb.novel.service.impl.ReadingTicketServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReadingTicketServiceImplTest {
    private ReadingTicketMapper mapper;
    private ReadingTicketServiceImpl service;
    private final Date now = Date.from(Instant.parse("2027-01-01T00:00:00Z"));

    @BeforeEach
    void setUp() {
        mapper = mock(ReadingTicketMapper.class);
        service = new ReadingTicketServiceImpl(mapper);
    }

    @Test
    void grantCreatesImmutableLedgerLotAndProjection() {
        ReadingTicketAccountRow account = account(91L, 0L, 4L);
        ReadingTicketLedgerRow ledger = ledger(101L, 5L);
        when(mapper.lockAccountByUserId(11L)).thenReturn(account);
        when(mapper.insertLedger(any())).thenReturn(1);
        when(mapper.selectLedgerByIdempotencyKey("SUBSCRIPTION:11:2027-01"))
            .thenReturn(null, ledger);
        when(mapper.insertLot(11L, "SUBSCRIPTION", "2027-01", 5L, 101L,
            now, Date.from(Instant.parse("2027-02-01T00:00:00Z")), "v1")).thenReturn(1);
        when(mapper.creditAccount(91L, 4L, 5L)).thenReturn(1);

        assertThat(service.grant(new ReadingTicketGrantCommand(11L, 5L, "SUBSCRIPTION",
            "2027-01", "SUBSCRIPTION:11:2027-01", now,
            Date.from(Instant.parse("2027-02-01T00:00:00Z")), "SYSTEM", null,
            "Vé đọc tháng 01/2027", "v1"))).isEqualTo(ReadingTicketPostResult.POSTED);
    }

    @Test
    void unlockConsumesFifoLotAndCreatesPermanentChapterEntitlement() {
        ReadingTicketAccountRow account = account(91L, 2L, 4L);
        ReadingTicketLotRow lot = new ReadingTicketLotRow();
        lot.setId(201L);
        lot.setRemainingAmount(2L);
        lot.setVersion(3L);
        ReadingTicketLedgerRow ledger = ledger(301L, -1L);
        ChapterEntitlementRow entitlement = new ChapterEntitlementRow();
        entitlement.setId(401L);
        entitlement.setUserId(11L);
        entitlement.setBookIndexId(31L);
        when(mapper.selectActiveEntitlement(11L, 31L, now)).thenReturn(null, entitlement);
        when(mapper.selectActiveEntitlementForUpdate(11L, 31L, now)).thenReturn(null);
        when(mapper.lockAccountByUserId(11L)).thenReturn(account);
        when(mapper.selectLedgerByIdempotencyKey("READING_TICKET_UNLOCK:11:31:req_0001"))
            .thenReturn(null, ledger);
        when(mapper.insertLedger(any())).thenReturn(1);
        when(mapper.lockSpendableLots(11L, now, 20)).thenReturn(List.of(lot));
        when(mapper.consumeLot(201L, 3L, 1L, now)).thenReturn(1);
        when(mapper.insertLotAllocation(301L, 201L, 1L, 1L)).thenReturn(1);
        when(mapper.debitAccount(91L, 4L, 1L)).thenReturn(1);
        when(mapper.insertChapterEntitlement(11L, 21L, 31L, "READING_TICKET", "301",
            now, null, "READING_TICKET_UNLOCK:11:31:req_0001", "v1")).thenReturn(1);

        ReadingTicketUnlockResult result = service.unlockChapter(new ReadingTicketUnlockCommand(
            11L, 21L, 31L, "req_0001", now, "v1", 20));

        assertThat(result.status()).isEqualTo(ReadingTicketPostResult.POSTED);
        assertThat(result.entitlementId()).isEqualTo(401L);
        assertThat(result.availableBalance()).isEqualTo(1L);
    }

    @Test
    void existingEntitlementNeverSpendsAnotherTicket() {
        ChapterEntitlementRow entitlement = new ChapterEntitlementRow();
        entitlement.setId(401L);
        when(mapper.selectActiveEntitlement(11L, 31L, now)).thenReturn(entitlement);
        ReadingTicketAccountRow account = account(91L, 2L, 4L);
        when(mapper.selectAccount(11L)).thenReturn(account);

        ReadingTicketUnlockResult result = service.unlockChapter(new ReadingTicketUnlockCommand(
            11L, 21L, 31L, "req_0002", now, "v1", 20));

        assertThat(result.status()).isEqualTo(ReadingTicketPostResult.ALREADY_ENTITLED);
        assertThat(result.availableBalance()).isEqualTo(2L);
        verify(mapper, never()).insertLedger(any());
    }

    @Test
    void expiryClosesDueLotsAndUpdatesProjectionInOneLedger() {
        ReadingTicketAccountRow account = account(91L, 5L, 4L);
        ReadingTicketLotRow first = lot(201L, 2L, 1L);
        ReadingTicketLotRow second = lot(202L, 3L, 2L);
        ReadingTicketLedgerRow ledger = ledger(301L, -5L);
        when(mapper.lockAccountByUserId(11L)).thenReturn(account);
        when(mapper.lockExpiredLotsByUser(11L, now, 100)).thenReturn(List.of(first, second));
        when(mapper.insertLedger(any())).thenReturn(1);
        when(mapper.selectLedgerByIdempotencyKey(anyString())).thenReturn(ledger);
        when(mapper.expireLot(201L, 1L, 2L, now)).thenReturn(1);
        when(mapper.expireLot(202L, 2L, 3L, now)).thenReturn(1);
        when(mapper.insertLotAllocation(301L, 201L, 2L, 0L)).thenReturn(1);
        when(mapper.insertLotAllocation(301L, 202L, 3L, 0L)).thenReturn(1);
        when(mapper.expireFromAccount(91L, 4L, 5L)).thenReturn(1);

        ReadingTicketExpiryResult result = service.expireDueLots(11L, now, "v1", 100);

        assertThat(result).isEqualTo(new ReadingTicketExpiryResult(2, 5));
        ArgumentCaptor<ReadingTicketLedgerWrite> write =
            ArgumentCaptor.forClass(ReadingTicketLedgerWrite.class);
        verify(mapper).insertLedger(write.capture());
        assertThat(write.getValue().getEntryType()).isEqualTo("EXPIRE");
        assertThat(write.getValue().getAmount()).isEqualTo(-5L);
        assertThat(write.getValue().getBalanceAfter()).isZero();
    }

    @Test
    void expiryIsNoOpWhenNoLotIsDue() {
        when(mapper.lockAccountByUserId(11L)).thenReturn(account(91L, 5L, 4L));
        when(mapper.lockExpiredLotsByUser(11L, now, 100)).thenReturn(List.of());

        assertThat(service.expireDueLots(11L, now, "v1", 100))
            .isEqualTo(ReadingTicketExpiryResult.EMPTY);
        verify(mapper, never()).insertLedger(any());
    }

    @Test
    void expiryFailsClosedWhenProjectionCannotCoverDueLots() {
        when(mapper.lockAccountByUserId(11L)).thenReturn(account(91L, 1L, 4L));
        when(mapper.lockExpiredLotsByUser(11L, now, 100))
            .thenReturn(List.of(lot(201L, 2L, 1L)));

        org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> service.expireDueLots(11L, now, "v1", 100))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("projection");
        verify(mapper, never()).insertLedger(any());
    }

    @Test
    void historyQueriesClampPaginationAndRemainUserScoped() {
        ReadingTicketLedgerRow ledger = ledger(301L, 5L);
        ReadingTicketLotHistoryRow lot = new ReadingTicketLotHistoryRow();
        lot.setId(201L);
        lot.setUserId(11L);
        lot.setGrantedAmount(5L);
        lot.setRemainingAmount(3L);
        when(mapper.countLedgerByUser(11L)).thenReturn(1L);
        when(mapper.selectLedgerByUser(11L, 0L, 100)).thenReturn(List.of(ledger));
        when(mapper.countLotsByUser(11L)).thenReturn(1L);
        when(mapper.selectLotsByUser(11L, 20L, 20)).thenReturn(List.of(lot));

        ReadingTicketLedgerPage ledgerPage = service.listLedgerHistory(11L, 0, 1000);
        ReadingTicketLotPage lotPage = service.listLotHistory(11L, 2, 20);

        assertThat(ledgerPage).isEqualTo(new ReadingTicketLedgerPage(
            List.of(ledger), 1L, 1, 100));
        assertThat(lotPage).isEqualTo(new ReadingTicketLotPage(
            List.of(lot), 1L, 2, 20));
        verify(mapper).selectLedgerByUser(11L, 0L, 100);
        verify(mapper).selectLotsByUser(11L, 20L, 20);
    }

    private ReadingTicketAccountRow account(long id, long balance, long version) {
        ReadingTicketAccountRow row = new ReadingTicketAccountRow();
        row.setId(id);
        row.setUserId(11L);
        row.setAvailableBalance(balance);
        row.setVersion(version);
        row.setStatus("ACTIVE");
        return row;
    }

    private ReadingTicketLedgerRow ledger(long id, long amount) {
        ReadingTicketLedgerRow row = new ReadingTicketLedgerRow();
        row.setId(id);
        row.setUserId(11L);
        row.setAmount(amount);
        return row;
    }

    private ReadingTicketLotRow lot(long id, long remaining, long version) {
        ReadingTicketLotRow row = new ReadingTicketLotRow();
        row.setId(id);
        row.setUserId(11L);
        row.setRemainingAmount(remaining);
        row.setVersion(version);
        row.setStatus("ACTIVE");
        return row;
    }
}
