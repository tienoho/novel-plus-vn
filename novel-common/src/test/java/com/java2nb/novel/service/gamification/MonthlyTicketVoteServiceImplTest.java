package com.java2nb.novel.service.gamification;

import com.java2nb.novel.core.exception.BusinessException;
import com.java2nb.novel.mapper.MonthlyTicketMapper;
import com.java2nb.novel.service.impl.MonthlyTicketServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MonthlyTicketVoteServiceImplTest {

    private static final long USER_ID = 8_101L;
    private static final long BOOK_ID = 9_202L;
    private static final long SEASON_ID = 33L;
    private static final Date NOW = Date.from(Instant.parse("2026-08-15T03:00:00Z"));
    private static final LocalDate LOCAL_DATE = LocalDate.of(2026, 8, 15);
    private static final String CLIENT_REQUEST_ID = "req-20260815-0001";
    private static final String IP_HASH = "a".repeat(64);

    private MonthlyTicketMapper mapper;
    private MonthlyTicketServiceImpl service;
    private TicketPolicy policy;
    private TicketAccountRow account;
    private TicketBookEligibilityRow eligibleBook;
    private TicketVoteRow postedVote;
    private TicketRankCounterRow rankCounter;

    @BeforeEach
    void setUp() {
        mapper = mock(MonthlyTicketMapper.class);
        service = new MonthlyTicketServiceImpl(mapper);
        policy = new TicketPolicy("v1", 60, 10, 20, 50, 100, 50, false);

        TicketSeasonRow season = new TicketSeasonRow();
        season.setId(SEASON_ID);
        season.setPeriodCode("2026-08");
        season.setStatus("OPEN");
        season.setPolicyVersion("v1");

        account = new TicketAccountRow();
        account.setId(71L);
        account.setUserId(USER_ID);
        account.setAvailableBalance(10L);
        account.setStatus("ACTIVE");
        account.setVersion(4L);

        eligibleBook = new TicketBookEligibilityRow();
        eligibleBook.setBookId(BOOK_ID);
        eligibleBook.setAuthorId(501L);
        eligibleBook.setOwnerUserId(502L);
        eligibleBook.setStatus((byte) 1);
        eligibleBook.setAuditStatus((byte) 1);
        eligibleBook.setBlocked(false);
        eligibleBook.setSelfOwned(false);
        eligibleBook.setCollaborator(false);

        TicketLedgerRow ledger = new TicketLedgerRow();
        ledger.setId(601L);

        postedVote = new TicketVoteRow();
        postedVote.setId(701L);
        postedVote.setSeasonId(SEASON_ID);
        postedVote.setBookId(BOOK_ID);
        postedVote.setAuthorId(501L);
        postedVote.setUserId(USER_ID);
        postedVote.setTicketCount(4L);
        postedVote.setClientRequestId(CLIENT_REQUEST_ID);

        rankCounter = new TicketRankCounterRow();
        rankCounter.setSeasonId(SEASON_ID);
        rankCounter.setBookId(BOOK_ID);
        rankCounter.setTotalTickets(14L);

        when(mapper.selectOpenSeason(any(Date.class))).thenReturn(season);
        when(mapper.lockAccountByUserId(USER_ID)).thenReturn(account);
        when(mapper.selectAccount(USER_ID)).thenReturn(account);
        when(mapper.selectBookEligibility(BOOK_ID, USER_ID)).thenReturn(eligibleBook);
        when(mapper.tryConsumeDailyQuota(anyLong(), any(LocalDate.class), anyLong(), anyInt(), anyInt()))
            .thenReturn(1);
        when(mapper.tryConsumeBookQuota(anyLong(), anyLong(), anyLong(), anyLong(), anyInt()))
            .thenReturn(1);
        when(mapper.insertLedger(anyString(), anyLong(), anyString(), anyLong(), anyLong(), anyString(),
            anyString(), anyString(), anyString(), any(), any(), any(), anyString(), any(), any(),
            anyString())).thenReturn(1);
        when(mapper.selectLedgerByIdempotencyKey(anyString())).thenReturn(ledger);
        when(mapper.debitAccount(71L, 4L, 4L)).thenReturn(1);
        when(mapper.insertVote(anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong(),
            anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(1);
        when(mapper.insertRankVoterIgnore(SEASON_ID, BOOK_ID, USER_ID)).thenReturn(1);
        // MySQL có thể trả 2 cho nhánh UPDATE của ON DUPLICATE KEY; service phải chấp nhận > 0.
        when(mapper.upsertRankCounter(anyLong(), anyLong(), anyLong(), anyInt(), any(Date.class)))
            .thenReturn(2);
        when(mapper.countSeasonStillOpen(SEASON_ID, NOW)).thenReturn(1);
        when(mapper.selectVoteByIdempotencyKey(anyString())).thenReturn(postedVote);
        when(mapper.selectRankCounter(SEASON_ID, BOOK_ID)).thenReturn(rankCounter);
    }

    @Test
    void spendsLotsInExpiryOrderAndPostsOneVote() {
        TicketLotRow first = lot(801L, 2L, 1L);
        TicketLotRow second = lot(802L, 5L, 2L);
        when(mapper.lockSpendableLots(USER_ID, NOW, 50)).thenReturn(List.of(first, second));
        when(mapper.consumeLot(anyLong(), anyLong(), anyLong(), any(Date.class))).thenReturn(1);
        when(mapper.insertLotAllocation(anyLong(), anyLong(), anyLong(), anyLong())).thenReturn(1);

        TicketVoteResult result = service.castVote(command(4), policy);

        assertThat(result.status()).isEqualTo(TicketPostResult.POSTED);
        assertThat(result.voteId()).isEqualTo(701L);
        assertThat(result.bookTotal()).isEqualTo(14L);
        assertThat(result.availableBalance()).isEqualTo(6L);
        InOrder order = inOrder(mapper);
        order.verify(mapper).consumeLot(801L, 1L, 2L, NOW);
        order.verify(mapper).consumeLot(802L, 2L, 2L, NOW);
        verify(mapper).insertLotAllocation(601L, 801L, 2L, 0L);
        verify(mapper).insertLotAllocation(601L, 802L, 2L, 3L);
    }

    @Test
    void rejectsWhenProjectionSaysEnoughButLotsDoNot() {
        when(mapper.lockSpendableLots(USER_ID, NOW, 50)).thenReturn(List.of(lot(801L, 2L, 1L)));
        when(mapper.consumeLot(anyLong(), anyLong(), anyLong(), any(Date.class))).thenReturn(1);
        when(mapper.insertLotAllocation(anyLong(), anyLong(), anyLong(), anyLong())).thenReturn(1);

        assertThatThrownBy(() -> service.castVote(command(4), policy))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("không khớp");
        verify(mapper, never()).debitAccount(anyLong(), anyLong(), anyLong());
        verify(mapper, never()).insertVote(anyLong(), anyLong(), anyLong(), anyLong(), anyLong(),
            anyLong(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void insufficientAccountBalanceStopsBeforeQuota() {
        account.setAvailableBalance(3L);

        assertThatThrownBy(() -> service.castVote(command(4), policy))
            .isInstanceOf(BusinessException.class);
        verify(mapper, never()).tryConsumeDailyQuota(anyLong(), any(), anyLong(), anyInt(), anyInt());
    }

    @Test
    void dailyAndBookQuotasAreHardDatabaseBarriers() {
        when(mapper.tryConsumeDailyQuota(anyLong(), any(), anyLong(), anyInt(), anyInt())).thenReturn(0);
        assertThatThrownBy(() -> service.castVote(command(4), policy))
            .isInstanceOf(BusinessException.class);
        verify(mapper, never()).insertBookQuotaIgnore(anyLong(), anyLong(), anyLong());

        when(mapper.tryConsumeDailyQuota(anyLong(), any(), anyLong(), anyInt(), anyInt())).thenReturn(1);
        when(mapper.tryConsumeBookQuota(anyLong(), anyLong(), anyLong(), anyLong(), anyInt())).thenReturn(0);
        assertThatThrownBy(() -> service.castVote(command(4), policy))
            .isInstanceOf(BusinessException.class);
        verify(mapper, never()).lockSpendableLots(anyLong(), any(), anyInt());
    }

    @Test
    void unlinkedAuthorFailsClosed() {
        eligibleBook.setOwnerUserId(null);

        assertThatThrownBy(() -> service.castVote(command(4), policy))
            .isInstanceOf(BusinessException.class);
        verify(mapper, never()).tryConsumeDailyQuota(anyLong(), any(), anyLong(), anyInt(), anyInt());
    }

    @Test
    void authorCannotVoteForOwnedBook() {
        eligibleBook.setSelfOwned(true);

        assertThatThrownBy(() -> service.castVote(command(4), policy))
            .isInstanceOf(BusinessException.class);
        verify(mapper, never()).tryConsumeDailyQuota(anyLong(), any(), anyLong(), anyInt(), anyInt());
    }

    @Test
    void collaboratorCannotVoteForCollaboratedBook() {
        eligibleBook.setCollaborator(true);

        assertThatThrownBy(() -> service.castVote(command(4), policy))
            .isInstanceOf(BusinessException.class);
        verify(mapper, never()).tryConsumeDailyQuota(anyLong(), any(), anyLong(), anyInt(), anyInt());
    }

    @Test
    void crawledBookIsRejectedByDefaultPolicy() {
        eligibleBook.setCrawlSourceId(88);

        assertThatThrownBy(() -> service.castVote(command(4), policy))
            .isInstanceOf(BusinessException.class);
        verify(mapper, never()).tryConsumeDailyQuota(anyLong(), any(), anyLong(), anyInt(), anyInt());
    }

    @Test
    void replayReturnsPreviousResultWithoutConsumingQuotaAgain() {
        when(mapper.selectVoteByUserClientRequest(USER_ID, CLIENT_REQUEST_ID)).thenReturn(postedVote);
        account.setAvailableBalance(6L);

        TicketVoteResult result = service.castVote(command(4), policy);

        assertThat(result.status()).isEqualTo(TicketPostResult.ALREADY_POSTED);
        assertThat(result.availableBalance()).isEqualTo(6L);
        verify(mapper, never()).lockAccountByUserId(anyLong());
        verify(mapper, never()).tryConsumeDailyQuota(anyLong(), any(), anyLong(), anyInt(), anyInt());
    }

    @Test
    void sameClientRequestWithDifferentPayloadIsRejected() {
        postedVote.setBookId(99_999L);
        when(mapper.selectVoteByUserClientRequest(USER_ID, CLIENT_REQUEST_ID)).thenReturn(postedVote);

        assertThatThrownBy(() -> service.castVote(command(4), policy))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void closingSeasonAtFinalRecheckRollsBackTheVote() {
        when(mapper.lockSpendableLots(USER_ID, NOW, 50)).thenReturn(List.of(lot(801L, 10L, 1L)));
        when(mapper.consumeLot(anyLong(), anyLong(), anyLong(), any(Date.class))).thenReturn(1);
        when(mapper.insertLotAllocation(anyLong(), anyLong(), anyLong(), anyLong())).thenReturn(1);
        when(mapper.countSeasonStillOpen(SEASON_ID, NOW)).thenReturn(0);

        assertThatThrownBy(() -> service.castVote(command(4), policy))
            .isInstanceOf(BusinessException.class);
    }

    private TicketVoteCommand command(int count) {
        return new TicketVoteCommand(USER_ID, BOOK_ID, count, CLIENT_REQUEST_ID, IP_HASH, NOW, LOCAL_DATE);
    }

    private TicketLotRow lot(long id, long remaining, long version) {
        TicketLotRow lot = new TicketLotRow();
        lot.setId(id);
        lot.setUserId(USER_ID);
        lot.setRemainingAmount(remaining);
        lot.setVersion(version);
        return lot;
    }
}
