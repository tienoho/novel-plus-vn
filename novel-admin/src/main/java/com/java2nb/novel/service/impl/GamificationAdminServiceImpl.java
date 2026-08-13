package com.java2nb.novel.service.impl;

import com.java2nb.novel.config.GamificationAdminSettings;
import com.java2nb.novel.dao.GamificationAdminDao;
import com.java2nb.novel.service.GamificationAdminService;
import com.java2nb.novel.service.gamification.MonthlyTicketService;
import com.java2nb.novel.service.gamification.MonthlyRankingService;
import com.java2nb.novel.service.gamification.MonthlyRankDriftRow;
import com.java2nb.novel.service.gamification.MonthlySeasonRow;
import com.java2nb.novel.service.gamification.GamificationProfileRow;
import com.java2nb.novel.service.gamification.GamificationProgressService;
import com.java2nb.novel.service.gamification.SeasonPhaseResult;
import com.java2nb.novel.service.gamification.TicketGrantCommand;
import com.java2nb.novel.service.gamification.TicketPostResult;
import com.java2nb.novel.service.gamification.AuthorRewardService;
import com.java2nb.novel.service.gamification.AuthorRewardAllocationRow;
import com.java2nb.novel.service.gamification.RewardCampaignCommand;
import com.java2nb.novel.service.gamification.RewardCampaignRow;
import com.java2nb.novel.service.gamification.RewardShareRule;
import com.java2nb.novel.service.gamification.QuestCampaignConfigService;
import com.java2nb.novel.service.gamification.QuestCampaignDraftCommand;
import com.java2nb.novel.service.gamification.QuestCampaignRow;
import com.java2nb.novel.service.gamification.QuestRewardCommand;
import com.java2nb.novel.service.gamification.TicketRiskService;
import com.java2nb.novel.service.gamification.TicketRiskReviewRow;
import com.java2nb.novel.service.gamification.GamificationPublicPolicyService;
import com.java2nb.novel.service.gamification.GamificationPublicPolicyRow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class GamificationAdminServiceImpl implements GamificationAdminService {

    private final GamificationAdminDao dao;
    private final MonthlyTicketService monthlyTicketService;
    private final MonthlyRankingService monthlyRankingService;
    private final AuthorRewardService authorRewardService;
    private final QuestCampaignConfigService questCampaignConfigService;
    private final GamificationProgressService progressService;
    private final TicketRiskService ticketRiskService;
    private final GamificationPublicPolicyService publicPolicyService;
    private final GamificationAdminSettings settings;
    private final Clock clock;
    private final String ownerInstance;

    @Autowired
    public GamificationAdminServiceImpl(GamificationAdminDao dao,
                                        MonthlyTicketService monthlyTicketService,
                                        MonthlyRankingService monthlyRankingService,
                                        AuthorRewardService authorRewardService,
                                         QuestCampaignConfigService questCampaignConfigService,
                                         GamificationProgressService progressService,
                                         TicketRiskService ticketRiskService,
                                         GamificationPublicPolicyService publicPolicyService,
                                         GamificationAdminSettings settings) {
        this(dao, monthlyTicketService, monthlyRankingService, authorRewardService,
            questCampaignConfigService, progressService, ticketRiskService, publicPolicyService,
            settings, Clock.systemUTC(),
            "admin-season-" + UUID.randomUUID());
    }

    GamificationAdminServiceImpl(GamificationAdminDao dao,
                                 MonthlyTicketService monthlyTicketService,
                                 MonthlyRankingService monthlyRankingService,
                                 AuthorRewardService authorRewardService,
                                  QuestCampaignConfigService questCampaignConfigService,
                                  GamificationProgressService progressService,
                                  TicketRiskService ticketRiskService,
                                  GamificationPublicPolicyService publicPolicyService,
                                  GamificationAdminSettings settings, Clock clock,
                                 String ownerInstance) {
        this.dao = dao;
        this.monthlyTicketService = monthlyTicketService;
        this.monthlyRankingService = monthlyRankingService;
        this.authorRewardService = authorRewardService;
        this.questCampaignConfigService = questCampaignConfigService;
        this.progressService = progressService;
        this.ticketRiskService = ticketRiskService;
        this.publicPolicyService = publicPolicyService;
        this.settings = settings;
        this.clock = clock;
        this.ownerInstance = ownerInstance;
    }

    @Override
    public List<Map<String, Object>> listAccounts(Map<String, Object> params) {
        return dao.listAccounts(params);
    }

    @Override
    public int countAccounts(Map<String, Object> params) {
        return dao.countAccounts(params);
    }

    @Override
    public List<Map<String, Object>> listLedger(Map<String, Object> params) {
        return dao.listLedger(params);
    }

    @Override
    public int countLedger(Map<String, Object> params) {
        return dao.countLedger(params);
    }

    @Override
    public List<Map<String, Object>> listJobs(Map<String, Object> params) {
        return dao.listJobs(params);
    }

    @Override
    public int countJobs(Map<String, Object> params) {
        return dao.countJobs(params);
    }

    @Override
    public List<Map<String, Object>> listSeasons(Map<String, Object> params) {
        return dao.listSeasons(params);
    }

    @Override
    public int countSeasons(Map<String, Object> params) {
        return dao.countSeasons(params);
    }

    @Override
    public List<Map<String, Object>> listRewardCampaigns(Map<String, Object> params) {
        return dao.listRewardCampaigns(params);
    }

    @Override
    public int countRewardCampaigns(Map<String, Object> params) {
        return dao.countRewardCampaigns(params);
    }

    @Override
    public List<Map<String, Object>> listRewardAllocations(Map<String, Object> params) {
        return dao.listRewardAllocations(params);
    }

    @Override
    public int countRewardAllocations(Map<String, Object> params) {
        return dao.countRewardAllocations(params);
    }

    @Override
    public List<Map<String, Object>> listQuestCampaigns(Map<String, Object> params) {
        return dao.listQuestCampaigns(params);
    }

    @Override
    public int countQuestCampaigns(Map<String, Object> params) {
        return dao.countQuestCampaigns(params);
    }

    @Override
    public List<Map<String, Object>> listQuestRewards(Map<String, Object> params) {
        return dao.listQuestRewards(params);
    }

    @Override
    public int countQuestRewards(Map<String, Object> params) {
        return dao.countQuestRewards(params);
    }

    @Override
    public TicketPostResult grant(long userId, long amount, String clientRequestId,
                                  long effectiveAtMillis, String reason, long actorId,
                                  boolean canAdjust) {
        if (!settings.getTicket().isEnabled() || !settings.isConfigured()) {
            throw new IllegalStateException("Tính năng Ngọn Đuốc chưa được bật hoặc cấu hình chưa hợp lệ");
        }
        if (userId <= 0 || actorId <= 0 || amount <= 0) {
            throw new IllegalArgumentException("Người dùng, quản trị viên và số Đuốc phải hợp lệ");
        }
        if (amount > settings.getTicket().getMaxGrantPerBatch() && !canAdjust) {
            throw new SecurityException("Cấp vượt hạn mức cần quyền novel:gamification:adjust");
        }
        String requestId = normalizeRequestId(clientRequestId);
        String normalizedReason = normalizeReason(reason);
        Instant effectiveAt = Instant.ofEpochMilli(effectiveAtMillis);
        Instant now = clock.instant();
        if (effectiveAt.isAfter(now.plus(Duration.ofMinutes(5)))
            || effectiveAt.isBefore(now.minus(Duration.ofDays(1)))) {
            throw new IllegalArgumentException("Thời điểm cấp Ngọn Đuốc nằm ngoài cửa sổ cho phép");
        }
        Instant expireAt = effectiveAt.plus(Duration.ofDays(
            settings.getTicket().getLotValidityDays()));
        String sourceRef = "ADMIN:" + requestId;
        String idempotencyKey = "ADMIN_GRANT:" + requestId + ':' + userId;
        return monthlyTicketService.grant(new TicketGrantCommand(userId, amount, "ADMIN_GRANT",
            sourceRef, idempotencyKey, Date.from(effectiveAt), Date.from(expireAt), "ADMIN",
            actorId, normalizedReason, settings.getPolicyVersion()));
    }

    @Override
    public SeasonPhaseResult closeSeason(long seasonId, long actorId) {
        requireSeasonOperation(seasonId, actorId);
        return monthlyRankingService.closeSeason(seasonId, Date.from(clock.instant()));
    }

    @Override
    public SeasonPhaseResult pauseSeason(long seasonId, String reason, long actorId) {
        requireSeasonOperation(seasonId, actorId);
        return monthlyRankingService.pauseSnapshot(
            seasonId, actorId, reason, Date.from(clock.instant()));
    }

    @Override
    public SeasonPhaseResult retrySeason(long seasonId, long actorId) {
        requireSeasonOperation(seasonId, actorId);
        return monthlyRankingService.retrySnapshot(seasonId, ownerInstance, Date.from(clock.instant()),
            settings.getSeason().getCloseDrainSeconds(), settings.getJob().getLeaseSeconds(),
            settings.getJob().getBatchSize());
    }

    @Override
    public List<MonthlyRankDriftRow> reconcileSeason(long seasonId) {
        if (seasonId <= 0) {
            throw new IllegalArgumentException("Mã kỳ xếp hạng không hợp lệ");
        }
        return monthlyRankingService.reconcile(seasonId);
    }

    @Override
    public SeasonPhaseResult finalizeSeason(long seasonId, long actorId) {
        requireSeasonOperation(seasonId, actorId);
        return monthlyRankingService.finalizeSeason(
            seasonId, actorId, Date.from(clock.instant()));
    }

    @Override
    public MonthlySeasonRow createSpecialSeason(String periodCode, String seasonType,
                                                long startAtMillis, long endAtMillis,
                                                long voteCutoffAtMillis, long actorId) {
        if (!settings.getSeason().isEnabled() || !settings.isConfigured() || actorId <= 0) {
            throw new IllegalStateException(
                "Kỳ xếp hạng Ngọn Đuốc chưa được bật, cấu hình chưa hợp lệ hoặc thiếu quản trị viên");
        }
        return monthlyRankingService.createSpecialSeason(periodCode, seasonType,
            Date.from(Instant.ofEpochMilli(startAtMillis)), Date.from(Instant.ofEpochMilli(endAtMillis)),
            Date.from(Instant.ofEpochMilli(voteCutoffAtMillis)), settings.resolveZoneId(),
            settings.getPolicyVersion());
    }

    @Override
    public List<Map<String, Object>> listTickerNicknames(Map<String, Object> params) {
        return dao.listTickerNicknames(params);
    }

    @Override
    public int countTickerNicknames(Map<String, Object> params) {
        return dao.countTickerNicknames(params);
    }

    @Override
    public List<Map<String, Object>> listRiskReviews(Map<String, Object> params) {
        return dao.listRiskReviews(params);
    }

    @Override
    public int countRiskReviews(Map<String, Object> params) {
        return dao.countRiskReviews(params);
    }

    @Override
    public TicketRiskReviewRow reviewRisk(long assessmentId, long expectedVersion,
                                          String decision, String reason, long actorId) {
        return ticketRiskService.review(assessmentId, expectedVersion, decision, actorId,
            reason, Date.from(clock.instant()));
    }

    @Override
    public List<Map<String, Object>> listPublicPolicies(Map<String, Object> params) {
        return dao.listPublicPolicies(params);
    }

    @Override
    public int countPublicPolicies(Map<String, Object> params) {
        return dao.countPublicPolicies(params);
    }

    @Override
    public GamificationPublicPolicyRow createPublicPolicy(String policyVersion, String title,
                                                          String contentText, long actorId) {
        return publicPolicyService.createDraft(policyVersion, title, contentText, actorId);
    }

    @Override
    public GamificationPublicPolicyRow publishPublicPolicy(long policyId, long expectedVersion,
                                                           long actorId) {
        return publicPolicyService.publish(policyId, expectedVersion, actorId,
            Date.from(clock.instant()));
    }

    @Override
    public GamificationProfileRow moderateTickerVisibility(long userId, boolean hide, String reason,
                                                            long actorId) {
        if (!settings.isConfigured() || userId <= 0 || actorId <= 0) {
            throw new IllegalArgumentException("Yêu cầu kiểm duyệt bảng chạy không hợp lệ");
        }
        return progressService.adminSetTickerOptOut(userId, hide, actorId, reason,
            settings.getPolicyVersion());
    }

    @Override
    public RewardCampaignRow calculateRewardCampaign(long seasonId, long budgetXu, String sharesBps) {
        if (!settings.isConfigured()) {
            throw new IllegalStateException("Cấu hình gamification chưa hợp lệ");
        }
        return authorRewardService.calculateAllocations(new RewardCampaignCommand(
            seasonId, budgetXu, parseShares(sharesBps), settings.getPolicyVersion()));
    }

    @Override
    public RewardCampaignRow approveRewardCampaign(long campaignId, long actorId) {
        if (!settings.isConfigured() || actorId <= 0) {
            throw new IllegalArgumentException("Yêu cầu duyệt campaign không hợp lệ");
        }
        return authorRewardService.approveCampaign(campaignId, actorId, Date.from(clock.instant()));
    }

    @Override
    public int postRewardCampaign(long campaignId) {
        if (!settings.getReward().isEnabled() || !settings.isConfigured()) {
            throw new IllegalStateException("Chức năng ghi thưởng tác giả chưa được bật");
        }
        int posted = 0;
        while (true) {
            List<Long> ids = authorRewardService.listApprovedAllocationIds(
                campaignId, settings.getJob().getBatchSize());
            if (ids.isEmpty()) {
                return posted;
            }
            for (Long id : ids) {
                authorRewardService.postPendingReward(id, Date.from(clock.instant()));
                posted++;
            }
        }
    }

    @Override
    public AuthorRewardAllocationRow clawbackReward(long allocationId, String reason, long actorId) {
        if (!settings.isConfigured()) {
            throw new IllegalStateException("Cấu hình gamification chưa hợp lệ");
        }
        return authorRewardService.clawback(allocationId, actorId, reason,
            Date.from(clock.instant()), settings.getReward().getClaimWindowDays());
    }

    @Override
    public QuestCampaignRow createQuestCampaign(String campaignCode, long startAtMillis,
                                                 long endAtMillis, long actorId) {
        requireConfigActor(actorId);
        return questCampaignConfigService.createDraft(new QuestCampaignDraftCommand(campaignCode,
            Date.from(Instant.ofEpochMilli(startAtMillis)), Date.from(Instant.ofEpochMilli(endAtMillis)),
            settings.getPolicyVersion()));
    }

    @Override
    public void saveQuestReward(long campaignId, String questCode, long expAmount,
                                long ticketAmount, long actorId) {
        requireConfigActor(actorId);
        questCampaignConfigService.replaceReward(campaignId,
            new QuestRewardCommand(questCode, expAmount, ticketAmount));
    }

    @Override
    public QuestCampaignRow activateQuestCampaign(long campaignId, long actorId) {
        requireConfigActor(actorId);
        return questCampaignConfigService.activate(campaignId, Date.from(clock.instant()));
    }

    @Override
    public QuestCampaignRow closeQuestCampaign(long campaignId, long actorId) {
        requireConfigActor(actorId);
        return questCampaignConfigService.close(campaignId);
    }

    private List<RewardShareRule> parseShares(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!normalized.matches("\\d+(?:\\s*,\\s*\\d+){0,9}")) {
            throw new IllegalArgumentException("Cơ cấu basis point không hợp lệ");
        }
        String[] parts = normalized.split("\\s*,\\s*");
        List<RewardShareRule> shares = new java.util.ArrayList<>();
        for (int index = 0; index < parts.length; index++) {
            int basisPoints;
            try {
                basisPoints = Integer.parseInt(parts[index]);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Cơ cấu basis point không hợp lệ", exception);
            }
            shares.add(new RewardShareRule(index + 1, basisPoints));
        }
        return List.copyOf(shares);
    }

    private void requireSeasonOperation(long seasonId, long actorId) {
        if (!settings.getSeason().isEnabled() || !settings.isConfigured()) {
            throw new IllegalStateException("Kỳ xếp hạng Ngọn Đuốc chưa được bật hoặc cấu hình chưa hợp lệ");
        }
        if (seasonId <= 0 || actorId <= 0) {
            throw new IllegalArgumentException("Kỳ xếp hạng và quản trị viên phải hợp lệ");
        }
    }

    private void requireConfigActor(long actorId) {
        if (!settings.isConfigured() || actorId <= 0) {
            throw new IllegalArgumentException("Quản trị viên hoặc cấu hình gamification không hợp lệ");
        }
    }

    private String normalizeRequestId(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() < 8 || normalized.length() > 64
            || !normalized.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("Mã yêu cầu cấp Đuốc không hợp lệ");
        }
        return normalized;
    }

    private String normalizeReason(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() < 10 || normalized.length() > 255) {
            throw new IllegalArgumentException("Lý do cấp Đuốc phải dài từ 10 đến 255 ký tự");
        }
        return normalized;
    }
}
