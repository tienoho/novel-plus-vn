package com.java2nb.novel.service.impl;

import com.java2nb.novel.config.GamificationAdminSettings;
import com.java2nb.novel.dao.GamificationAdminDao;
import com.java2nb.novel.service.gamification.MonthlyTicketService;
import com.java2nb.novel.service.gamification.MonthlyRankingService;
import com.java2nb.novel.service.gamification.MonthlySeasonRow;
import com.java2nb.novel.service.gamification.GamificationProfileRow;
import com.java2nb.novel.service.gamification.GamificationProgressService;
import com.java2nb.novel.service.gamification.SeasonPhaseResult;
import com.java2nb.novel.service.gamification.AuthorRewardService;
import com.java2nb.novel.service.gamification.RewardCampaignCommand;
import com.java2nb.novel.service.gamification.RewardCampaignRow;
import com.java2nb.novel.service.gamification.TicketGrantCommand;
import com.java2nb.novel.service.gamification.TicketPostResult;
import com.java2nb.novel.service.gamification.QuestCampaignConfigService;
import com.java2nb.novel.service.gamification.QuestCampaignDraftCommand;
import com.java2nb.novel.service.gamification.QuestCampaignRow;
import com.java2nb.novel.service.gamification.QuestRewardCommand;
import com.java2nb.novel.service.gamification.TicketRiskService;
import com.java2nb.novel.service.gamification.TicketRiskReviewRow;
import com.java2nb.novel.service.gamification.GamificationPublicPolicyRow;
import com.java2nb.novel.service.gamification.GamificationPublicPolicyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GamificationAdminServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-08-15T03:00:00Z");

    private MonthlyTicketService monthlyTicketService;
    private MonthlyRankingService monthlyRankingService;
    private AuthorRewardService authorRewardService;
    private QuestCampaignConfigService questCampaignConfigService;
    private GamificationProgressService progressService;
    private TicketRiskService ticketRiskService;
    private GamificationPublicPolicyService publicPolicyService;
    private GamificationAdminServiceImpl service;

    @BeforeEach
    void setUp() {
        GamificationAdminSettings settings = new GamificationAdminSettings();
        settings.getTicket().setEnabled(true);
        settings.getSeason().setEnabled(true);
        settings.getReward().setEnabled(true);
        monthlyTicketService = mock(MonthlyTicketService.class);
        monthlyRankingService = mock(MonthlyRankingService.class);
        authorRewardService = mock(AuthorRewardService.class);
        questCampaignConfigService = mock(QuestCampaignConfigService.class);
        progressService = mock(GamificationProgressService.class);
        ticketRiskService = mock(TicketRiskService.class);
        publicPolicyService = mock(GamificationPublicPolicyService.class);
        service = new GamificationAdminServiceImpl(mock(GamificationAdminDao.class),
            monthlyTicketService, monthlyRankingService, authorRewardService,
            questCampaignConfigService, progressService, ticketRiskService, publicPolicyService, settings,
            Clock.fixed(NOW, ZoneOffset.UTC), "admin-season-test");
    }

    @Test
    void grantsThroughCommonLedgerWithAdminAudit() {
        when(monthlyTicketService.grant(any(TicketGrantCommand.class)))
            .thenReturn(TicketPostResult.POSTED);

        assertThat(service.grant(101L, 5L, "request_20260815_001", NOW.toEpochMilli(),
            "Bù Đuốc do sự cố nhiệm vụ", 9L, false)).isEqualTo(TicketPostResult.POSTED);

        ArgumentCaptor<TicketGrantCommand> captor = ArgumentCaptor.forClass(TicketGrantCommand.class);
        verify(monthlyTicketService).grant(captor.capture());
        TicketGrantCommand command = captor.getValue();
        assertThat(command.userId()).isEqualTo(101L);
        assertThat(command.sourceType()).isEqualTo("ADMIN_GRANT");
        assertThat(command.operatorType()).isEqualTo("ADMIN");
        assertThat(command.operatorId()).isEqualTo(9L);
        assertThat(command.expireAt().toInstant()).isEqualTo(NOW.plusSeconds(60L * 86_400));
    }

    @Test
    void grantAboveBatchLimitRequiresAdjustPermission() {
        assertThatThrownBy(() -> service.grant(101L, 1_001L, "request_20260815_002",
            NOW.toEpochMilli(), "Cấp Đuốc cho chiến dịch đặc biệt", 9L, false))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("novel:gamification:adjust");

        verify(monthlyTicketService, never()).grant(any(TicketGrantCommand.class));
    }

    @Test
    void retryWithSameRequestTimestampBuildsTheSameCommand() {
        when(monthlyTicketService.grant(any(TicketGrantCommand.class)))
            .thenReturn(TicketPostResult.POSTED, TicketPostResult.ALREADY_POSTED);

        service.grant(101L, 5L, "request_20260815_003", NOW.toEpochMilli(),
            "Bù Đuốc do sự cố nhiệm vụ", 9L, false);
        service.grant(101L, 5L, "request_20260815_003", NOW.toEpochMilli(),
            "Bù Đuốc do sự cố nhiệm vụ", 9L, false);

        ArgumentCaptor<TicketGrantCommand> captor = ArgumentCaptor.forClass(TicketGrantCommand.class);
        verify(monthlyTicketService, org.mockito.Mockito.times(2)).grant(captor.capture());
        assertThat(captor.getAllValues().get(0)).isEqualTo(captor.getAllValues().get(1));
    }

    @Test
    void retrySeasonUsesConfiguredLeaseBatchAndAuthenticatedActorContext() {
        when(monthlyRankingService.retrySnapshot(71L, "admin-season-test", java.util.Date.from(NOW),
            60, 300, 500)).thenReturn(
                SeasonPhaseResult.owner(71L, 901L, "REVIEW"));

        SeasonPhaseResult result = service.retrySeason(71L, 9L);

        assertThat(result.outcome()).isEqualTo(SeasonPhaseResult.Outcome.OWNER);
        verify(monthlyRankingService).retrySnapshot(71L, "admin-season-test",
            java.util.Date.from(NOW), 60, 300, 500);
    }

    @Test
    void finalizePassesAuthenticatedAdminToCommonStateMachine() {
        when(monthlyRankingService.finalizeSeason(71L, 9L, java.util.Date.from(NOW)))
            .thenReturn(SeasonPhaseResult.owner(71L, 901L, "FINALIZED"));

        service.finalizeSeason(71L, 9L);

        verify(monthlyRankingService).finalizeSeason(71L, 9L, java.util.Date.from(NOW));
    }

    @Test
    void parsesRewardBasisPointsWithoutInventingRanks() {
        RewardCampaignRow campaign = new RewardCampaignRow();
        when(authorRewardService.calculateAllocations(any(RewardCampaignCommand.class)))
            .thenReturn(campaign);

        service.calculateRewardCampaign(71L, 101L, "5000, 3000, 2000");

        ArgumentCaptor<RewardCampaignCommand> captor =
            ArgumentCaptor.forClass(RewardCampaignCommand.class);
        verify(authorRewardService).calculateAllocations(captor.capture());
        assertThat(captor.getValue().shares()).extracting("rank")
            .containsExactly(1, 2, 3);
        assertThat(captor.getValue().shares()).extracting("basisPoints")
            .containsExactly(5000, 3000, 2000);
    }

    @Test
    void rewardPostingRespectsKillSwitch() {
        GamificationAdminSettings disabled = new GamificationAdminSettings();
        GamificationAdminServiceImpl disabledService = new GamificationAdminServiceImpl(
            mock(GamificationAdminDao.class), monthlyTicketService, monthlyRankingService,
            authorRewardService, questCampaignConfigService, progressService, ticketRiskService,
            publicPolicyService, disabled,
            Clock.fixed(NOW, ZoneOffset.UTC), "admin-test");

        assertThatThrownBy(() -> disabledService.postRewardCampaign(501L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("chưa được bật");
        verify(authorRewardService, never()).listApprovedAllocationIds(anyLong(), anyInt());
    }

    @Test
    void createsQuestCampaignWithServerPolicyAndActivatesAtServerTime() {
        QuestCampaignRow draft = new QuestCampaignRow();
        draft.setId(71L);
        when(questCampaignConfigService.createDraft(any(QuestCampaignDraftCommand.class)))
            .thenReturn(draft);
        when(questCampaignConfigService.activate(71L, java.util.Date.from(NOW)))
            .thenReturn(draft);

        service.createQuestCampaign("summer_2027",
            Instant.parse("2027-06-01T00:00:00Z").toEpochMilli(),
            Instant.parse("2027-07-01T00:00:00Z").toEpochMilli(), 9L);
        service.activateQuestCampaign(71L, 9L);

        ArgumentCaptor<QuestCampaignDraftCommand> campaignCaptor =
            ArgumentCaptor.forClass(QuestCampaignDraftCommand.class);
        verify(questCampaignConfigService).createDraft(campaignCaptor.capture());
        assertThat(campaignCaptor.getValue().campaignCode()).isEqualTo("SUMMER_2027");
        assertThat(campaignCaptor.getValue().policyVersion()).isEqualTo("v1");
        verify(questCampaignConfigService).activate(71L, java.util.Date.from(NOW));
    }

    @Test
    void delegatesQuestRewardWithoutAllowingNegativeAmounts() {
        service.saveQuestReward(71L, "DAILY_READING", 20L, 2L, 9L);

        verify(questCampaignConfigService).replaceReward(71L,
            new QuestRewardCommand("DAILY_READING", 20L, 2L));
    }

    @Test
    void createsSpecialSeasonWithServerZoneAndPolicy() {
        MonthlySeasonRow season = new MonthlySeasonRow();
        season.setSeasonType("ANNIVERSARY");
        when(monthlyRankingService.createSpecialSeason(
            org.mockito.ArgumentMatchers.eq("ky-ky-niem-2026"), org.mockito.ArgumentMatchers.eq("ANNIVERSARY"),
            any(), any(), any(), any(), org.mockito.ArgumentMatchers.eq("v1")))
            .thenReturn(season);

        MonthlySeasonRow result = service.createSpecialSeason("ky-ky-niem-2026", "ANNIVERSARY",
            Instant.parse("2026-09-01T00:00:00Z").toEpochMilli(),
            Instant.parse("2026-09-08T00:00:00Z").toEpochMilli(),
            Instant.parse("2026-09-07T00:00:00Z").toEpochMilli(), 9L);

        assertThat(result.getSeasonType()).isEqualTo("ANNIVERSARY");
    }

    @Test
    void specialSeasonCreationRespectsSeasonKillSwitch() {
        GamificationAdminSettings disabled = new GamificationAdminSettings();
        GamificationAdminServiceImpl disabledService = new GamificationAdminServiceImpl(
            mock(GamificationAdminDao.class), monthlyTicketService, monthlyRankingService,
            authorRewardService, questCampaignConfigService, progressService, ticketRiskService,
            publicPolicyService, disabled,
            Clock.fixed(NOW, ZoneOffset.UTC), "admin-test");

        assertThatThrownBy(() -> disabledService.createSpecialSeason("ky-ky-niem-2026", "ANNIVERSARY",
            NOW.toEpochMilli(), NOW.plusSeconds(3600).toEpochMilli(),
            NOW.plusSeconds(1800).toEpochMilli(), 9L))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void moderatesTickerVisibilityThroughProgressServiceAudit() {
        GamificationProfileRow profile = new GamificationProfileRow();
        when(progressService.adminSetTickerOptOut(101L, true, 9L,
            "Nickname vi phạm quy định cộng đồng", "v1")).thenReturn(profile);

        GamificationProfileRow result = service.moderateTickerVisibility(101L, true,
            "Nickname vi phạm quy định cộng đồng", 9L);

        assertThat(result).isSameAs(profile);
        verify(progressService).adminSetTickerOptOut(101L, true, 9L,
            "Nickname vi phạm quy định cộng đồng", "v1");
    }

    @Test
    void reviewsRiskThroughCommonOptimisticService() {
        TicketRiskReviewRow approved = new TicketRiskReviewRow();
        approved.setAssessmentId(71L);
        approved.setStatus("APPROVED");
        when(ticketRiskService.review(71L, 2L, "APPROVED", 9L,
            "Đã xác minh hoạt động hợp lệ", java.util.Date.from(NOW))).thenReturn(approved);

        assertThat(service.reviewRisk(71L, 2L, "APPROVED",
            "Đã xác minh hoạt động hợp lệ", 9L)).isSameAs(approved);
    }

    @Test
    void delegatesPublicPolicyCreationWithAuthenticatedAdmin() {
        GamificationPublicPolicyRow draft = new GamificationPublicPolicyRow();
        draft.setId(81L);
        when(publicPolicyService.createDraft("v2", "Luật chơi v2",
            "Nội dung luật chơi đủ dài để phát hành công khai cho độc giả trên nền tảng.", 9L))
            .thenReturn(draft);

        assertThat(service.createPublicPolicy("v2", "Luật chơi v2",
            "Nội dung luật chơi đủ dài để phát hành công khai cho độc giả trên nền tảng.", 9L))
            .isSameAs(draft);
    }

    @Test
    void delegatesPublicPolicyPublishWithServerTimeAndOptimisticVersion() {
        GamificationPublicPolicyRow published = new GamificationPublicPolicyRow();
        published.setId(81L);
        published.setStatus("PUBLISHED");
        when(publicPolicyService.publish(81L, 3L, 9L, java.util.Date.from(NOW)))
            .thenReturn(published);

        assertThat(service.publishPublicPolicy(81L, 3L, 9L)).isSameAs(published);
        verify(publicPolicyService).publish(81L, 3L, 9L, java.util.Date.from(NOW));
    }
}
