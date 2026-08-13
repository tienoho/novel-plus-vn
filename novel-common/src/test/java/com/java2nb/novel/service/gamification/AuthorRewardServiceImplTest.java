package com.java2nb.novel.service.gamification;

import com.java2nb.novel.mapper.AuthorRewardMapper;
import com.java2nb.novel.service.impl.AuthorRewardServiceImpl;
import com.java2nb.novel.service.wallet.WalletLedgerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthorRewardServiceImplTest {

    private static final long SEASON_ID = 71L;
    private static final long SNAPSHOT_ID = 901L;
    private static final Date NOW = Date.from(Instant.parse("2026-09-05T00:00:00Z"));

    private AuthorRewardMapper mapper;
    private WalletLedgerService walletLedgerService;
    private AuthorRewardServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(AuthorRewardMapper.class);
        walletLedgerService = mock(WalletLedgerService.class);
        service = new AuthorRewardServiceImpl(mapper, walletLedgerService);
    }

    @Test
    void sevenRankOddBudgetFloorsSharesAndAddsRemainderToFirstRank() {
        List<RewardShareRule> shares = List.of(
            new RewardShareRule(1, 3001), new RewardShareRule(2, 2000),
            new RewardShareRule(3, 1500), new RewardShareRule(4, 1200),
            new RewardShareRule(5, 900), new RewardShareRule(6, 800),
            new RewardShareRule(7, 599));
        stubCalculation(shares, distinctRanks(7));
        ArgumentCaptor<AuthorRewardAllocationRow> captor =
            ArgumentCaptor.forClass(AuthorRewardAllocationRow.class);
        when(mapper.insertAllocation(captor.capture())).thenReturn(1);

        service.calculateAllocations(new RewardCampaignCommand(SEASON_ID, 101L, shares, "v1", 7L));

        List<AuthorRewardAllocationRow> allocations = captor.getAllValues();
        assertThat(allocations).hasSize(7);
        assertThat(allocations.stream().mapToLong(AuthorRewardAllocationRow::getAmountXu).sum())
            .isEqualTo(101L);
        assertThat(allocations.get(0).getAmountXu()).isEqualTo(31L);
        assertThat(allocations.get(0).getRoundingAdjustmentXu()).isEqualTo(1L);
        assertThat(allocations.subList(1, 7))
            .allMatch(row -> row.getRoundingAdjustmentXu() == 0L);
    }

    @Test
    void duplicateAuthorKeepsRankButReceivesOnlyHighestPrize() {
        List<RewardShareRule> shares = List.of(
            new RewardShareRule(1, 5000), new RewardShareRule(2, 3000),
            new RewardShareRule(3, 2000));
        List<MonthlyRankRow> ranks = distinctRanks(3);
        ranks.get(1).setAuthorId(ranks.get(0).getAuthorId());
        stubCalculation(shares, ranks);
        ArgumentCaptor<AuthorRewardAllocationRow> captor =
            ArgumentCaptor.forClass(AuthorRewardAllocationRow.class);
        when(mapper.insertAllocation(captor.capture())).thenReturn(1);

        service.calculateAllocations(new RewardCampaignCommand(SEASON_ID, 100L, shares, "v1", 7L));

        AuthorRewardAllocationRow duplicate = captor.getAllValues().get(1);
        assertThat(duplicate.getStatus()).isEqualTo("SKIPPED_DUPLICATE_AUTHOR");
        assertThat(duplicate.getAmountXu()).isZero();
        assertThat(duplicate.getReason()).contains("giải cao hơn");
    }

    @Test
    void finalizerCannotApproveTheRewardCampaign() {
        RewardCampaignRow campaign = campaign("DRAFT");
        when(mapper.lockCampaignById(501L)).thenReturn(campaign);
        MonthlySeasonRow season = finalizedSeason();
        season.setFinalizedBy(9L);
        when(mapper.selectSeasonByPeriodForReward("2026-08")).thenReturn(season);

        assertThatThrownBy(() -> service.approveCampaign(501L, 9L, NOW))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("không được đồng thời");

        verify(mapper, never()).approveCampaign(anyLong(), anyLong(), anyLong(), any());
    }

    @Test
    void postingPendingRewardAndAllocationStateShareOneServiceTransaction() {
        AuthorRewardAllocationRow approved = allocation("APPROVED");
        AuthorRewardAllocationRow posted = allocation("POSTED_PENDING");
        posted.setPostedAt(NOW);
        when(mapper.lockAllocationById(801L)).thenReturn(approved);
        Date releaseEligibleAt = Date.from(NOW.toInstant().plusSeconds(7L * 86_400));
        when(mapper.markPostedPending(801L, 3L, NOW, releaseEligibleAt)).thenReturn(1);
        when(mapper.selectAllocationById(801L)).thenReturn(posted);

        AuthorRewardAllocationRow result = service.postPendingReward(801L, NOW, 7);

        assertThat(result.getStatus()).isEqualTo("POSTED_PENDING");
        verify(mapper).markPostedPending(801L, 3L, NOW, releaseEligibleAt);
        verify(walletLedgerService).creditAuthorRewardPending(22L, 200L,
            "2026-08:101:1:22", "MONTHLY_AUTHOR_REWARD:2026-08:101:1:22",
            "Thưởng xếp hạng Ngọn Đuốc tháng");
    }

    @Test
    void clawbackUsesTheExistingLedgerReversalOnlyInsideClaimWindow() {
        AuthorRewardAllocationRow pending = allocation("POSTED_PENDING");
        pending.setPostedAt(Date.from(NOW.toInstant().minusSeconds(10L * 86_400L)));
        pending.setReleaseEligibleAt(Date.from(NOW.toInstant().plusSeconds(86_400L)));
        AuthorRewardAllocationRow clawed = allocation("CLAWED_BACK");
        when(mapper.lockAllocationById(801L)).thenReturn(pending);
        when(mapper.markClawedBack(801L, 3L, NOW,
            "ADMIN:9:Thu hồi do kết quả khiếu nại")).thenReturn(1);
        when(mapper.selectAllocationById(801L)).thenReturn(clawed);

        service.clawback(801L, 9L, "Thu hồi do kết quả khiếu nại", NOW);

        verify(walletLedgerService).reverseTransaction(
            "MONTHLY_AUTHOR_REWARD:2026-08:101:1:22", "MONTHLY_AUTHOR_REWARD_CLAWBACK",
            "2026-08:101:1:22", "MONTHLY_AUTHOR_REWARD_CLAWBACK:2026-08:101:1:22",
            "Thu hồi do kết quả khiếu nại");
    }

    @Test
    void releasedRewardMovesFromClearingToAuthorWallet() {
        AuthorRewardAllocationRow pending = allocation("POSTED_PENDING");
        AuthorRewardAllocationRow released = allocation("RELEASED");
        when(mapper.lockAllocationById(801L)).thenReturn(pending);
        when(mapper.markReleased(801L, 3L, NOW)).thenReturn(1);
        when(mapper.selectAllocationById(801L)).thenReturn(released);

        service.releaseMaturedReward(801L, NOW);

        verify(walletLedgerService).releaseAuthorReward(22L, 200L,
            "2026-08:101:1:22", "MONTHLY_AUTHOR_REWARD_RELEASE:2026-08:101:1:22",
            "Giải phóng thưởng xếp hạng Ngọn Đuốc");
    }

    private void stubCalculation(List<RewardShareRule> shares, List<MonthlyRankRow> ranks) {
        MonthlySeasonRow season = finalizedSeason();
        when(mapper.selectSeasonForReward(SEASON_ID)).thenReturn(season);
        when(mapper.selectCampaignByPeriod("2026-08")).thenReturn(null, campaign("DRAFT"));
        when(mapper.insertCampaign(anyString(), anyLong(), anyString(), anyString(), anyLong()))
            .thenReturn(1);
        when(mapper.selectSnapshotRewardRows(SNAPSHOT_ID, shares.size())).thenReturn(ranks);
    }

    private MonthlySeasonRow finalizedSeason() {
        MonthlySeasonRow season = new MonthlySeasonRow();
        season.setId(SEASON_ID);
        season.setPeriodCode("2026-08");
        season.setStatus("FINALIZED");
        season.setSnapshotId(SNAPSHOT_ID);
        season.setPolicyVersion("v1");
        season.setFinalizedBy(8L);
        return season;
    }

    private RewardCampaignRow campaign(String status) {
        RewardCampaignRow row = new RewardCampaignRow();
        row.setId(501L);
        row.setPeriodCode("2026-08");
        row.setStatus(status);
        row.setBudgetXu(101L);
        row.setStructureJson("structure");
        row.setPolicyVersion("v1");
        row.setVersion(2L);
        return row;
    }

    private List<MonthlyRankRow> distinctRanks(int count) {
        List<MonthlyRankRow> rows = new ArrayList<>();
        for (int rank = 1; rank <= count; rank++) {
            MonthlyRankRow row = new MonthlyRankRow();
            row.setRankNo(rank);
            row.setBookId(100L + rank);
            row.setAuthorId(200L + rank);
            rows.add(row);
        }
        return rows;
    }

    private AuthorRewardAllocationRow allocation(String status) {
        AuthorRewardAllocationRow row = new AuthorRewardAllocationRow();
        row.setId(801L);
        row.setAllocationNo("2026-08:101:1:22");
        row.setAuthorId(22L);
        row.setAmountXu(200L);
        row.setStatus(status);
        row.setVersion(3L);
        return row;
    }
}
