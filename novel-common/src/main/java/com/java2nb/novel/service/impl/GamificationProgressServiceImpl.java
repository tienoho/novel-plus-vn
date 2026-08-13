package com.java2nb.novel.service.impl;

import com.java2nb.novel.core.enums.ResponseStatus;
import com.java2nb.novel.core.exception.BusinessException;
import com.java2nb.novel.mapper.GamificationProgressMapper;
import com.java2nb.novel.mapper.MonthlyTicketMapper;
import com.java2nb.novel.service.gamification.GamificationProfileRow;
import com.java2nb.novel.service.gamification.GamificationProgressService;
import com.java2nb.novel.service.gamification.GamificationProfileSnapshot;
import com.java2nb.novel.service.gamification.LevelRuleRow;
import com.java2nb.novel.service.gamification.RealmCatalogRow;
import com.java2nb.novel.service.gamification.RealmUpdateResult;
import com.java2nb.novel.service.gamification.GamificationEventRow;
import com.java2nb.novel.service.gamification.QuestDefinitionRow;
import com.java2nb.novel.service.gamification.QuestCampaignRow;
import com.java2nb.novel.service.gamification.QuestProgressRow;
import com.java2nb.novel.service.gamification.QuestRewardSummary;
import com.java2nb.novel.service.gamification.QuestClaimCommand;
import com.java2nb.novel.service.gamification.QuestClaimResult;
import com.java2nb.novel.service.gamification.QuestClaimRow;
import com.java2nb.novel.service.gamification.UserExpLedgerRow;
import com.java2nb.novel.service.gamification.MonthlyTicketService;
import com.java2nb.novel.service.gamification.TicketGrantCommand;
import com.java2nb.novel.service.gamification.TicketLedgerRow;
import com.java2nb.novel.service.gamification.TicketAccountRow;
import com.java2nb.novel.service.gamification.GamificationEventRecorder;
import com.java2nb.novel.service.gamification.GamificationEventInputFactory;
import com.java2nb.novel.service.gamification.CheckInResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.Date;
import java.util.Objects;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GamificationProgressServiceImpl implements GamificationProgressService {

    private static final String DEFAULT_CAMPAIGN = "DEFAULT";

    private final GamificationProgressMapper mapper;
    private final MonthlyTicketService monthlyTicketService;
    private final MonthlyTicketMapper monthlyTicketMapper;
    private final GamificationEventRecorder eventRecorder;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GamificationProfileRow getProfile(long userId, String ruleVersion) {
        validateIdentity(userId, ruleVersion);
        mapper.insertProfileIgnore(userId, ruleVersion);
        GamificationProfileRow profile = mapper.selectProfile(userId);
        if (profile == null) {
            throw new IllegalStateException("Không thể tạo hoặc đọc hồ sơ gamification");
        }
        return profile;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GamificationProfileSnapshot getProfileSnapshot(long userId, String ruleVersion) {
        GamificationProfileRow profile = getProfile(userId, ruleVersion);
        LevelRuleRow next = mapper.selectNextLevelRule(profile.getRuleVersion(), profile.getTotalExp());
        return new GamificationProfileSnapshot(profile, next == null ? null : next.getMinExp());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RealmUpdateResult updateRealm(long userId, String realmCode, long expectedVersion,
                                         Date changedAt, ZoneId zoneId, int cooldownHours,
                                         String ruleVersion) {
        validateIdentity(userId, ruleVersion);
        if (realmCode == null || realmCode.isBlank() || realmCode.length() > 32
            || expectedVersion < 0 || changedAt == null || zoneId == null || cooldownHours <= 0) {
            throw new BusinessException(ResponseStatus.GAMIFICATION_REALM_INVALID);
        }
        mapper.insertProfileIgnore(userId, ruleVersion);
        GamificationProfileRow profile = mapper.lockProfile(userId);
        if (profile == null) {
            throw new IllegalStateException("Không thể khóa hồ sơ gamification");
        }
        if (Objects.equals(profile.getRealmCode(), realmCode)) {
            return result(profile, profile.getRealmChangedAt(), cooldownHours);
        }
        if (!Objects.equals(profile.getVersion(), expectedVersion)) {
            throw new BusinessException(ResponseStatus.GAMIFICATION_PROFILE_VERSION_CONFLICT);
        }
        RealmCatalogRow realm = mapper.selectRealm(realmCode, ruleVersion);
        if (realm == null || !Boolean.TRUE.equals(realm.getActive())) {
            throw new BusinessException(ResponseStatus.GAMIFICATION_REALM_INVALID);
        }
        if (profile.getLevel() < realm.getMinLevel()) {
            throw new BusinessException(ResponseStatus.GAMIFICATION_REALM_LEVEL_REQUIRED);
        }
        requireCooldownElapsed(profile.getRealmChangedAt(), changedAt, zoneId, cooldownHours);
        if (mapper.updateRealm(userId, realmCode, changedAt, expectedVersion) != 1) {
            throw new BusinessException(ResponseStatus.GAMIFICATION_PROFILE_VERSION_CONFLICT);
        }
        if (mapper.insertProfileAudit(userId, "REALM", profile.getRealmCode(), realmCode,
            "USER", userId, "Người dùng đổi cảnh giới") != 1) {
            throw new IllegalStateException("Không thể ghi audit đổi cảnh giới");
        }
        GamificationProfileRow updated = mapper.selectProfile(userId);
        if (updated == null) {
            throw new IllegalStateException("Không đọc được hồ sơ sau khi đổi cảnh giới");
        }
        return result(updated, changedAt, cooldownHours);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int applyEvent(GamificationEventRow event) {
        Objects.requireNonNull(event, "Thiếu sự kiện cần áp dụng");
        List<QuestDefinitionRow> quests = mapper.selectActiveQuestsByEventType(event.getEventType(),
            event.getPolicyVersion());
        for (QuestDefinitionRow quest : quests) {
            String periodKey = periodKey(quest.getPeriodType(), event.getLocalDate());
            mapper.insertQuestProgressIgnore(event.getUserId(), quest.getQuestCode(), periodKey,
                quest.getTargetCount());
            mapper.incrementQuestProgress(event.getUserId(), quest.getQuestCode(), periodKey,
                event.getOccurredAt());
        }
        return quests.size();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CheckInResult checkIn(long userId, LocalDate localDate, Date checkedAt, ZoneId zoneId,
                                 String ruleVersion, String policyVersion,
                                 long runtimeConfigRevision) {
        validateIdentity(userId, ruleVersion);
        if (localDate == null || checkedAt == null || zoneId == null
            || policyVersion == null || policyVersion.isBlank() || policyVersion.length() > 32
            || !checkedAt.toInstant().atZone(zoneId).toLocalDate().equals(localDate)) {
            throw new IllegalArgumentException("Ngày hoặc chính sách điểm danh không hợp lệ");
        }
        mapper.insertProfileIgnore(userId, ruleVersion);
        GamificationProfileRow profile = mapper.lockProfile(userId);
        if (profile == null) {
            throw new IllegalStateException("Không thể khóa hồ sơ khi điểm danh");
        }
        String sourceKey = "CHECKIN:" + userId + ':' + localDate;
        LocalDate previousDate = profile.getLastCheckinDate();
        if (localDate.equals(previousDate)) {
            return new CheckInResult(profile, sourceKey, true);
        }
        if (previousDate != null && previousDate.isAfter(localDate)) {
            throw new IllegalStateException("Ngày điểm danh mới cũ hơn lịch sử hồ sơ");
        }
        int previousStreak = profile.getCheckinStreak() == null ? 0 : profile.getCheckinStreak();
        int streak = previousDate != null && previousDate.plusDays(1).equals(localDate)
            ? Math.addExact(previousStreak, 1) : 1;
        int longest = Math.max(profile.getLongestStreak() == null ? 0 : profile.getLongestStreak(),
            streak);
        if (mapper.updateCheckIn(userId, profile.getVersion(), localDate, streak, longest) != 1) {
            throw new IllegalStateException("Hồ sơ điểm danh đã được cập nhật đồng thời");
        }
        profile.setLastCheckinDate(localDate);
        profile.setCheckinStreak(streak);
        profile.setLongestStreak(longest);
        profile.setVersion(profile.getVersion() + 1);
        eventRecorder.ingest(GamificationEventInputFactory.create("CHECK_IN_COMPLETED", sourceKey,
            userId, null, checkedAt, "{\"streak\":" + streak + '}', zoneId, policyVersion,
            runtimeConfigRevision));
        return new CheckInResult(profile, sourceKey, false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuestProgressRow> listQuests(long userId, LocalDate localDate, Date observedAt,
                                             String policyVersion) {
        if (userId <= 0 || localDate == null || observedAt == null
            || policyVersion == null || policyVersion.isBlank()) {
            throw new IllegalArgumentException("Chủ thể hoặc ngày xem nhiệm vụ không hợp lệ");
        }
        QuestCampaignRow campaign = resolveActiveCampaign(observedAt, policyVersion);
        String campaignCode = campaign == null ? DEFAULT_CAMPAIGN : campaign.getCampaignCode();
        return mapper.selectQuestProgress(userId, localDate.toString(), periodKey("WEEKLY", localDate),
            campaignCode, policyVersion);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuestClaimResult claimQuest(QuestClaimCommand command) {
        QuestDefinitionRow quest = mapper.selectQuestByCode(command.questCode(),
            command.policyVersion());
        if (quest == null) {
            throw new BusinessException(ResponseStatus.GAMIFICATION_QUEST_NOT_FOUND);
        }
        String periodKey = periodKey(quest.getPeriodType(), command.localDate());
        QuestClaimRow existing = mapper.selectQuestClaim(command.userId(), command.questCode(), periodKey);
        if (existing != null) {
            return replayClaim(command, existing);
        }
        if (!Boolean.TRUE.equals(quest.getActive())) {
            throw new BusinessException(ResponseStatus.GAMIFICATION_QUEST_NOT_FOUND);
        }

        QuestProgressRow progress = mapper.lockQuestProgress(
            command.userId(), command.questCode(), periodKey);
        if (progress == null || progress.getCompletedAt() == null
            || progress.getCurrentCount() == null || progress.getTargetCount() == null
            || progress.getCurrentCount() < progress.getTargetCount()) {
            throw new BusinessException(ResponseStatus.GAMIFICATION_QUEST_NOT_COMPLETED);
        }
        existing = mapper.selectQuestClaim(command.userId(), command.questCode(), periodKey);
        if (existing != null) {
            return replayClaim(command, existing);
        }

        QuestRewardSelection rewardSelection = resolveQuestReward(command);
        QuestRewardSummary reward = rewardSelection.reward();
        if (reward == null || (reward.expAmount() <= 0 && reward.ticketAmount() <= 0)) {
            throw new BusinessException(ResponseStatus.GAMIFICATION_QUEST_REWARD_UNAVAILABLE);
        }

        mapper.insertProfileIgnore(command.userId(), command.ruleVersion());
        GamificationProfileRow profile = mapper.lockProfile(command.userId());
        if (profile == null) {
            throw new IllegalStateException("Không thể khóa hồ sơ khi nhận thưởng nhiệm vụ");
        }

        Long expLedgerId = postExpReward(command, periodKey, reward.expAmount(), profile,
            rewardSelection.policyVersion());
        Long ticketLedgerId = postTicketReward(command, periodKey, reward.ticketAmount(),
            rewardSelection.policyVersion());
        String claimKey = "QUEST_CLAIM:" + command.userId() + ':' + command.questCode() + ':' + periodKey;
        if (mapper.insertQuestClaim(command.userId(), command.questCode(), periodKey,
            rewardSelection.campaignCode(), reward.expAmount(), reward.ticketAmount(), expLedgerId,
            ticketLedgerId, claimKey, rewardSelection.policyVersion()) != 1) {
            throw new IllegalStateException("Không thể ghi lần nhận thưởng nhiệm vụ");
        }
        QuestClaimRow claim = mapper.selectQuestClaim(command.userId(), command.questCode(), periodKey);
        if (claim == null) {
            throw new IllegalStateException("Không đọc được lần nhận thưởng nhiệm vụ vừa tạo");
        }
        LevelRuleRow next = mapper.selectNextLevelRule(profile.getRuleVersion(), profile.getTotalExp());
        TicketAccountRow ticketAccount = monthlyTicketService.getOrCreateAccount(command.userId());
        return new QuestClaimResult(claim,
            new GamificationProfileSnapshot(profile, next == null ? null : next.getMinExp()),
            ticketAccount.getAvailableBalance(), false);
    }

    private Long postExpReward(QuestClaimCommand command, String periodKey, long amount,
                               GamificationProfileRow profile, String rewardPolicyVersion) {
        if (amount <= 0) {
            return null;
        }
        long balanceAfter = Math.addExact(profile.getTotalExp(), amount);
        LevelRuleRow levelRule = mapper.selectLevelRuleForExp(profile.getRuleVersion(), balanceAfter);
        if (levelRule == null) {
            throw new IllegalStateException("Không có quy tắc cấp độ phù hợp với EXP mới");
        }
        String sourceKey = "QUEST_EXP:" + command.userId() + ':' + command.questCode() + ':' + periodKey;
        if (mapper.insertExpLedger(command.userId(), sourceKey, "QUEST", amount, balanceAfter,
            profile.getRuleVersion(), rewardPolicyVersion) != 1) {
            throw new IllegalStateException("Không thể ghi sổ EXP nhiệm vụ");
        }
        UserExpLedgerRow ledger = mapper.selectExpLedgerBySourceKey(sourceKey);
        if (ledger == null) {
            throw new IllegalStateException("Không đọc được bút toán EXP nhiệm vụ vừa tạo");
        }
        int previousLevel = profile.getLevel();
        if (mapper.updateProfileExp(command.userId(), profile.getVersion(), balanceAfter,
            levelRule.getLevel(), levelRule.getFrameCode()) != 1) {
            throw new IllegalStateException("Hồ sơ EXP đã được cập nhật đồng thời");
        }
        profile.setTotalExp(balanceAfter);
        profile.setLevel(levelRule.getLevel());
        profile.setFrameCode(levelRule.getFrameCode());
        profile.setVersion(profile.getVersion() + 1);
        if (levelRule.getLevel() > previousLevel) {
            List<LevelRuleRow> reachedLevels = mapper.selectLevelRulesBetween(
                profile.getRuleVersion(), previousLevel, levelRule.getLevel());
            if (reachedLevels.size() != levelRule.getLevel() - previousLevel) {
                throw new IllegalStateException("Chuỗi quy tắc level không liên tục");
            }
            for (LevelRuleRow reachedLevel : reachedLevels) {
                eventRecorder.ingest(GamificationEventInputFactory.create("LEVEL_REACHED",
                    "GAMIFY:LEVEL_REACHED:" + command.userId() + ':' + reachedLevel.getLevel()
                        + ':' + profile.getRuleVersion(),
                    command.userId(), null, command.claimedAt(),
                    "{\"level\":" + reachedLevel.getLevel() + '}', command.zoneId(),
                    rewardPolicyVersion, command.runtimeConfigRevision()));
            }
        }
        return ledger.getId();
    }

    private Long postTicketReward(QuestClaimCommand command, String periodKey, long amount,
                                  String rewardPolicyVersion) {
        if (amount <= 0) {
            return null;
        }
        String sourceRef = command.questCode() + ':' + periodKey;
        String idempotencyKey = "QUEST_TICKET:" + command.userId() + ':' + sourceRef;
        Date effectiveAt = command.claimedAt();
        Date expireAt = Date.from(effectiveAt.toInstant().plus(
            command.ticketValidityDays(), ChronoUnit.DAYS));
        monthlyTicketService.grant(new TicketGrantCommand(command.userId(), amount, "QUEST",
            sourceRef, idempotencyKey, effectiveAt, expireAt, "SYSTEM", null, null,
            rewardPolicyVersion, command.runtimeConfigRevision()));
        TicketLedgerRow ledger = monthlyTicketMapper.selectLedgerByIdempotencyKey(idempotencyKey);
        if (ledger == null) {
            throw new IllegalStateException("Không đọc được bút toán Đuốc thưởng nhiệm vụ");
        }
        return ledger.getId();
    }

    private QuestClaimResult replayClaim(QuestClaimCommand command, QuestClaimRow existing) {
        GamificationProfileSnapshot snapshot = getProfileSnapshot(command.userId(), command.ruleVersion());
        TicketAccountRow account = monthlyTicketService.getOrCreateAccount(command.userId());
        return new QuestClaimResult(existing, snapshot, account.getAvailableBalance(), true);
    }

    private String periodKey(String periodType, LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("Sự kiện gamification thiếu ngày địa phương");
        }
        return switch (periodType) {
            case "DAILY" -> date.toString();
            case "WEEKLY" -> String.format("%04d-W%02d",
                date.get(WeekFields.ISO.weekBasedYear()), date.get(WeekFields.ISO.weekOfWeekBasedYear()));
            case "ONE_TIME" -> "ALL";
            default -> throw new IllegalStateException("Chu kỳ nhiệm vụ không được hỗ trợ: " + periodType);
        };
    }

    private QuestCampaignRow resolveActiveCampaign(Date observedAt, String policyVersion) {
        List<QuestCampaignRow> campaigns = mapper.selectActiveQuestCampaigns(observedAt,
            policyVersion);
        if (campaigns == null || campaigns.isEmpty()) {
            return null;
        }
        if (campaigns.size() > 1) {
            throw new IllegalStateException("Có nhiều chiến dịch nhiệm vụ ACTIVE chồng lấn");
        }
        QuestCampaignRow campaign = campaigns.get(0);
        if (campaign == null || campaign.getCampaignCode() == null
            || campaign.getCampaignCode().isBlank() || campaign.getPolicyVersion() == null
            || campaign.getPolicyVersion().isBlank()) {
            throw new IllegalStateException("Chiến dịch nhiệm vụ ACTIVE không hợp lệ");
        }
        return campaign;
    }

    private QuestRewardSelection resolveQuestReward(QuestClaimCommand command) {
        QuestCampaignRow campaign = resolveActiveCampaign(command.claimedAt(), command.policyVersion());
        QuestRewardSummary defaultReward = mapper.selectQuestRewardSummary(
            command.questCode(), DEFAULT_CAMPAIGN, command.policyVersion());
        if (campaign != null) {
            QuestRewardSummary campaignReward = mapper.selectQuestRewardSummary(
                command.questCode(), campaign.getCampaignCode(), campaign.getPolicyVersion());
            if (hasReward(campaignReward)) {
                return new QuestRewardSelection(campaign.getCampaignCode(),
                    campaign.getPolicyVersion(), mergeReward(campaignReward, defaultReward));
            }
        }
        return new QuestRewardSelection(DEFAULT_CAMPAIGN, command.policyVersion(),
            defaultReward);
    }

    private boolean hasReward(QuestRewardSummary reward) {
        return reward != null && (reward.expAmount() > 0 || reward.ticketAmount() > 0);
    }

    private QuestRewardSummary mergeReward(QuestRewardSummary campaignReward,
                                           QuestRewardSummary defaultReward) {
        long defaultExp = defaultReward == null ? 0 : defaultReward.expAmount();
        long defaultTicket = defaultReward == null ? 0 : defaultReward.ticketAmount();
        return new QuestRewardSummary(
            campaignReward.expAmount() > 0 ? campaignReward.expAmount() : defaultExp,
            campaignReward.ticketAmount() > 0 ? campaignReward.ticketAmount() : defaultTicket);
    }

    private record QuestRewardSelection(String campaignCode, String policyVersion,
                                        QuestRewardSummary reward) {
    }

    private void requireCooldownElapsed(Date previous, Date current, ZoneId zoneId, int hours) {
        if (previous == null) {
            return;
        }
        Instant previousInstant = previous.toInstant();
        Instant currentInstant = current.toInstant();
        LocalDate previousDate = previousInstant.atZone(zoneId).toLocalDate();
        LocalDate currentDate = currentInstant.atZone(zoneId).toLocalDate();
        if (previousDate.equals(currentDate)
            || currentInstant.isBefore(previousInstant.plus(hours, ChronoUnit.HOURS))) {
            throw new BusinessException(ResponseStatus.GAMIFICATION_REALM_COOLDOWN);
        }
    }

    private RealmUpdateResult result(GamificationProfileRow profile, Date changedAt, int hours) {
        Date cooldownUntil = changedAt == null ? null
            : Date.from(changedAt.toInstant().plus(hours, ChronoUnit.HOURS));
        return new RealmUpdateResult(profile, cooldownUntil);
    }

    private void validateIdentity(long userId, String ruleVersion) {
        if (userId <= 0 || ruleVersion == null || ruleVersion.isBlank() || ruleVersion.length() > 32) {
            throw new IllegalArgumentException("Chủ thể hoặc phiên bản luật gamification không hợp lệ");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public GamificationProfileRow updateTickerOptOut(long userId, boolean optOut, long expectedVersion,
                                                      String ruleVersion) {
        validateIdentity(userId, ruleVersion);
        mapper.insertProfileIgnore(userId, ruleVersion);
        GamificationProfileRow profile = mapper.lockProfile(userId);
        if (profile == null) {
            throw new IllegalStateException("Không thể khóa hồ sơ gamification");
        }
        if (Boolean.valueOf(optOut).equals(profile.getTickerOptOut())) {
            return profile;
        }
        if (!Objects.equals(profile.getVersion(), expectedVersion)) {
            throw new BusinessException(ResponseStatus.GAMIFICATION_PROFILE_VERSION_CONFLICT);
        }
        if (mapper.updateTickerOptOut(userId, optOut, expectedVersion) != 1) {
            throw new BusinessException(ResponseStatus.GAMIFICATION_PROFILE_VERSION_CONFLICT);
        }
        if (mapper.insertProfileAudit(userId, "TICKER_OPT",
            String.valueOf(profile.getTickerOptOut()), String.valueOf(optOut),
            "USER", userId, "Người dùng đổi hiển thị bảng chạy") != 1) {
            throw new IllegalStateException("Không thể ghi audit đổi tuỳ chọn bảng chạy");
        }
        GamificationProfileRow updated = mapper.selectProfile(userId);
        if (updated == null) {
            throw new IllegalStateException("Không đọc được hồ sơ sau khi đổi tuỳ chọn bảng chạy");
        }
        return updated;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public GamificationProfileRow adminSetTickerOptOut(long userId, boolean optOut, long operatorId,
                                                        String reason, String ruleVersion) {
        validateIdentity(userId, ruleVersion);
        if (operatorId <= 0) {
            throw new IllegalArgumentException("Quản trị viên kiểm duyệt không hợp lệ");
        }
        String normalizedReason = normalizeModerationReason(reason);
        mapper.insertProfileIgnore(userId, ruleVersion);
        GamificationProfileRow profile = mapper.lockProfile(userId);
        if (profile == null) {
            throw new IllegalStateException("Không thể khóa hồ sơ gamification");
        }
        if (Boolean.valueOf(optOut).equals(profile.getTickerOptOut())) {
            return profile;
        }
        if (mapper.updateTickerOptOut(userId, optOut, profile.getVersion()) != 1) {
            throw new BusinessException(ResponseStatus.GAMIFICATION_PROFILE_VERSION_CONFLICT);
        }
        if (mapper.insertProfileAudit(userId, "TICKER_OPT",
            String.valueOf(profile.getTickerOptOut()), String.valueOf(optOut),
            "ADMIN", operatorId, normalizedReason) != 1) {
            throw new IllegalStateException("Không thể ghi audit kiểm duyệt bảng chạy");
        }
        GamificationProfileRow updated = mapper.selectProfile(userId);
        if (updated == null) {
            throw new IllegalStateException("Không đọc được hồ sơ sau khi kiểm duyệt bảng chạy");
        }
        return updated;
    }

    private String normalizeModerationReason(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() < 10 || normalized.length() > 255) {
            throw new IllegalArgumentException("Lý do kiểm duyệt phải dài từ 10 đến 255 ký tự");
        }
        return normalized;
    }
}
