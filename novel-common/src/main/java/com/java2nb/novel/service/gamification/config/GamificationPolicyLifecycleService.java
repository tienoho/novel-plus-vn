package com.java2nb.novel.service.gamification.config;

import com.java2nb.novel.mapper.GamificationPolicyBundleMapper;
import com.java2nb.novel.service.gamification.GamificationPublicPolicyRow;
import com.java2nb.novel.service.gamification.LevelRewardPolicyRow;
import com.java2nb.novel.service.gamification.LevelRuleRow;
import com.java2nb.novel.service.gamification.QuestDefinitionRow;
import com.java2nb.novel.service.gamification.RealmCatalogRow;
import com.java2nb.novel.service.gamification.TicketRiskPolicyRow;
import com.java2nb.novel.service.gamification.TicketRiskRuleRow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.ToIntFunction;

@Service
public class GamificationPolicyLifecycleService {
    private static final Set<String> PERIOD_TYPES = Set.of("DAILY", "WEEKLY", "ONE_TIME");
    private static final Set<String> ABUSE_METRICS = Set.of(
        "ACCOUNT_AGE_HOURS", "USER_VOTES", "DEVICE_VOTES", "DEVICE_USERS", "IP_VOTES", "IP_USERS");

    private final GamificationPolicyBundleMapper mapper;
    private final GamificationPolicyHasher hasher;
    private final Clock clock;

    @Autowired
    public GamificationPolicyLifecycleService(GamificationPolicyBundleMapper mapper,
                                               GamificationPolicyHasher hasher) {
        this(mapper, hasher, Clock.systemUTC());
    }

    GamificationPolicyLifecycleService(GamificationPolicyBundleMapper mapper,
                                        GamificationPolicyHasher hasher, Clock clock) {
        this.mapper = mapper;
        this.hasher = hasher;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<GamificationPolicyBundleRow> list(int limit) {
        return mapper.selectBundles(Math.max(1, Math.min(limit, 200)));
    }

    @Transactional(readOnly = true)
    public GamificationPolicyBundleDetail get(String policyVersion) {
        return detail(requireVersion(policyVersion));
    }

    @Transactional(rollbackFor = Exception.class)
    public GamificationPolicyBundleDetail createDraft(String policyVersion, String sourceVersion,
                                                        long operatorId, String reason) {
        String target = requireVersion(policyVersion);
        String normalizedReason = requireReason(reason);
        requireOperator(operatorId);
        if (mapper.selectBundle(target) != null) {
            throw new IllegalStateException("Phiên bản policy đã tồn tại");
        }
        String source = sourceVersion == null || sourceVersion.isBlank()
            ? null : requireVersion(sourceVersion);
        if (source != null) {
            GamificationPolicyBundleRow sourceBundle = requireBundle(mapper.selectBundle(source));
            if (!"PUBLISHED".equals(sourceBundle.getStatus())) {
                throw new IllegalStateException("Chỉ được clone policy đã PUBLISHED");
            }
        }
        GamificationPolicyBundleRow bundle = new GamificationPolicyBundleRow();
        bundle.setPolicyVersion(target);
        bundle.setContentHash(hasher.hash(List.of()));
        bundle.setCreatedBy(operatorId);
        bundle.setChangeReason(normalizedReason);
        bundle.setVersion(0L);
        if (mapper.insertDraft(bundle) != 1 || bundle.getId() == null) {
            throw new IllegalStateException("Không thể tạo draft policy gamification");
        }
        if (source != null) {
            mapper.cloneLevelRules(source, target);
            mapper.cloneLevelRewards(source, target);
            mapper.cloneQuests(source, target);
            mapper.cloneQuestRewards(source, target);
            mapper.cloneRealms(source, target);
            mapper.cloneAbusePolicy(source, target);
            mapper.cloneAbuseRules(source, target);
            mapper.clonePublicPolicy(source, target);
        }
        String contentHash = currentHash(target);
        if (mapper.updateDraftHash(bundle.getId(), 0L, contentHash, normalizedReason) != 1) {
            throw conflict();
        }
        audit(bundle.getId(), "CREATED", null, "DRAFT", operatorId, normalizedReason,
            null, contentHash);
        return detail(target);
    }

    @Transactional(rollbackFor = Exception.class)
    public GamificationPolicyBundleDetail saveLevel(String policyVersion, long expectedVersion,
                                                      LevelRuleRow row, long operatorId, String reason) {
        validateLevel(row);
        return saveContent(policyVersion, expectedVersion, operatorId, reason,
            version -> mapper.upsertLevel(version, row));
    }

    @Transactional(rollbackFor = Exception.class)
    public GamificationPolicyBundleDetail saveLevelReward(String policyVersion, long expectedVersion,
                                                            LevelRewardPolicyRow row, long operatorId,
                                                            String reason) {
        if (row == null || row.getLevel() == null || row.getLevel() <= 1
            || row.getTicketAmount() == null || row.getTicketAmount() <= 0
            || row.getTicketValidityDays() == null || row.getTicketValidityDays() < 1
            || row.getTicketValidityDays() > 3650) {
            throw new IllegalArgumentException("Cấu hình thưởng level không hợp lệ");
        }
        return saveContent(policyVersion, expectedVersion, operatorId, reason,
            version -> mapper.upsertLevelReward(version, row));
    }

    @Transactional(rollbackFor = Exception.class)
    public GamificationPolicyBundleDetail saveQuest(String policyVersion, long expectedVersion,
                                                      QuestDefinitionRow row, long expReward,
                                                      long ticketReward, long operatorId,
                                                      String reason) {
        validateQuest(row, expReward, ticketReward);
        return saveContent(policyVersion, expectedVersion, operatorId, reason, version -> {
            int changed = mapper.upsertQuest(version, row);
            mapper.deleteDefaultQuestRewards(version, row.getQuestCode());
            if (expReward > 0) {
                changed += mapper.insertQuestReward(version, row.getQuestCode(), "EXP", expReward);
            }
            if (ticketReward > 0) {
                changed += mapper.insertQuestReward(version, row.getQuestCode(), "TICKET", ticketReward);
            }
            return changed;
        });
    }

    @Transactional(rollbackFor = Exception.class)
    public GamificationPolicyBundleDetail saveRealm(String policyVersion, long expectedVersion,
                                                      RealmCatalogRow row, long operatorId,
                                                      String reason) {
        if (row == null || invalidCode(row.getRealmCode(), 32) || invalidText(row.getNameKey(), 64)
            || row.getMinLevel() == null || row.getMinLevel() < 1 || row.getSortNo() == null
            || row.getSortNo() < 0 || row.getActive() == null) {
            throw new IllegalArgumentException("Cảnh giới không hợp lệ");
        }
        return saveContent(policyVersion, expectedVersion, operatorId, reason,
            version -> mapper.upsertRealm(version, row));
    }

    @Transactional(rollbackFor = Exception.class)
    public GamificationPolicyBundleDetail saveAbuseRule(String policyVersion, long expectedVersion,
                                                         int reviewScoreThreshold,
                                                         TicketRiskRuleRow row, long operatorId,
                                                         String reason) {
        validateAbuse(reviewScoreThreshold, row);
        return saveContent(policyVersion, expectedVersion, operatorId, reason, version ->
            mapper.upsertAbusePolicy(version, reviewScoreThreshold)
                + mapper.upsertAbuseRule(version, row));
    }

    @Transactional(rollbackFor = Exception.class)
    public GamificationPolicyBundleDetail savePublicPolicy(String policyVersion,
                                                            long expectedVersion, String title,
                                                            String contentText, long operatorId,
                                                            String reason) {
        String normalizedTitle = title == null ? "" : title.trim();
        String normalizedContent = contentText == null ? "" : contentText.trim();
        if (normalizedTitle.length() < 3 || normalizedTitle.length() > 160
            || normalizedContent.length() < 50 || normalizedContent.length() > 65_535) {
            throw new IllegalArgumentException("Luật chơi công khai không hợp lệ");
        }
        return saveContent(policyVersion, expectedVersion, operatorId, reason,
            version -> mapper.upsertPublicPolicy(version, normalizedTitle, normalizedContent));
    }

    @Transactional(rollbackFor = Exception.class)
    public GamificationPolicyBundleRow submit(String policyVersion, long expectedVersion,
                                               long operatorId, String reason) {
        String version = requireVersion(policyVersion);
        String normalizedReason = requireReason(reason);
        requireOperator(operatorId);
        GamificationPolicyBundleRow bundle = lockDraft(version, expectedVersion);
        validateComplete(detail(version));
        String hash = currentHash(version);
        Date now = Date.from(clock.instant());
        if (mapper.submit(bundle.getId(), expectedVersion, operatorId, now, normalizedReason, hash) != 1) {
            throw conflict();
        }
        audit(bundle.getId(), "SUBMITTED", "DRAFT", "PENDING_APPROVAL", operatorId,
            normalizedReason, bundle.getContentHash(), hash);
        return requireBundle(mapper.selectBundle(version));
    }

    @Transactional(rollbackFor = Exception.class)
    public GamificationPolicyBundleRow approve(String policyVersion, long expectedVersion,
                                                long operatorId, String reason) {
        String version = requireVersion(policyVersion);
        String normalizedReason = requireReason(reason);
        requireOperator(operatorId);
        GamificationPolicyBundleRow bundle = requireBundle(mapper.lockBundle(version));
        requireState(bundle, expectedVersion, "PENDING_APPROVAL");
        if (Objects.equals(bundle.getCreatedBy(), operatorId)) {
            throw new SecurityException("Người tạo policy không được tự phê duyệt");
        }
        Date now = Date.from(clock.instant());
        if (mapper.approve(bundle.getId(), expectedVersion, operatorId, now, normalizedReason) != 1) {
            throw conflict();
        }
        audit(bundle.getId(), "APPROVED", "PENDING_APPROVAL", "APPROVED", operatorId,
            normalizedReason, bundle.getContentHash(), bundle.getContentHash());
        return requireBundle(mapper.selectBundle(version));
    }

    @Transactional(rollbackFor = Exception.class)
    public GamificationPolicyBundleRow publish(String policyVersion, long expectedVersion,
                                                long operatorId, String reason) {
        String version = requireVersion(policyVersion);
        String normalizedReason = requireReason(reason);
        requireOperator(operatorId);
        GamificationPolicyBundleRow bundle = requireBundle(mapper.lockBundle(version));
        requireState(bundle, expectedVersion, "APPROVED");
        if (!Objects.equals(bundle.getContentHash(), currentHash(version))) {
            throw new IllegalStateException("Nội dung policy đã thay đổi sau khi submit");
        }
        Date now = Date.from(clock.instant());
        if (mapper.publishPublicPolicy(version, operatorId, now) != 1) {
            throw new IllegalStateException("Policy thiếu luật chơi DRAFT để phát hành");
        }
        if (mapper.insertPublicPolicyAudit(version, "PUBLISHED", "DRAFT", "PUBLISHED",
            operatorId) != 1) {
            throw new IllegalStateException("Không thể ghi audit luật chơi công khai");
        }
        if (mapper.publish(bundle.getId(), expectedVersion, operatorId, now, normalizedReason) != 1) {
            throw conflict();
        }
        audit(bundle.getId(), "PUBLISHED", "APPROVED", "PUBLISHED", operatorId,
            normalizedReason, bundle.getContentHash(), bundle.getContentHash());
        return requireBundle(mapper.selectBundle(version));
    }

    private GamificationPolicyBundleDetail saveContent(String rawVersion, long expectedVersion,
                                                        long operatorId, String reason,
                                                        ToIntFunction<String> writer) {
        String version = requireVersion(rawVersion);
        String normalizedReason = requireReason(reason);
        requireOperator(operatorId);
        GamificationPolicyBundleRow bundle = lockDraft(version, expectedVersion);
        String before = bundle.getContentHash();
        if (writer.applyAsInt(version) <= 0) {
            throw new IllegalStateException("Không thể lưu nội dung policy");
        }
        String after = currentHash(version);
        if (mapper.updateDraftHash(bundle.getId(), expectedVersion, after, normalizedReason) != 1) {
            throw conflict();
        }
        audit(bundle.getId(), "CONTENT_UPDATED", "DRAFT", "DRAFT", operatorId,
            normalizedReason, before, after);
        return detail(version);
    }

    private GamificationPolicyBundleDetail detail(String version) {
        return GamificationPolicyBundleDetail.builder()
            .bundle(requireBundle(mapper.selectBundle(version)))
            .levels(mapper.selectLevels(version))
            .levelRewards(mapper.selectLevelRewards(version))
            .quests(mapper.selectQuests(version))
            .questRewards(mapper.selectQuestRewards(version))
            .realms(mapper.selectRealms(version))
            .abusePolicy(mapper.selectAbusePolicy(version))
            .abuseRules(mapper.selectAbuseRules(version))
            .publicPolicy(mapper.selectPublicPolicy(version))
            .build();
    }

    private void validateComplete(GamificationPolicyBundleDetail detail) {
        if (detail.getLevels().isEmpty() || detail.getQuests().isEmpty() || detail.getRealms().isEmpty()
            || detail.getAbusePolicy() == null || detail.getPublicPolicy() == null) {
            throw new IllegalStateException("Policy chưa đủ level, quest, realm, abuse và luật công khai");
        }
        long previousExp = -1;
        int expectedLevel = 1;
        Set<Integer> levels = new HashSet<>();
        for (LevelRuleRow row : detail.getLevels()) {
            validateLevel(row);
            if (row.getLevel() != expectedLevel++ || row.getMinExp() <= previousExp) {
                throw new IllegalStateException("Chuỗi level phải liên tục và EXP tăng dần");
            }
            previousExp = row.getMinExp();
            levels.add(row.getLevel());
        }
        for (LevelRewardPolicyRow reward : detail.getLevelRewards()) {
            if (!levels.contains(reward.getLevel())) {
                throw new IllegalStateException("Thưởng level tham chiếu level không tồn tại");
            }
        }
        Set<String> rewardedQuests = new HashSet<>();
        for (GamificationQuestRewardRow reward : detail.getQuestRewards()) {
            if (reward.getAmount() != null && reward.getAmount() > 0) {
                rewardedQuests.add(reward.getQuestCode());
            }
        }
        for (QuestDefinitionRow quest : detail.getQuests()) {
            if (Boolean.TRUE.equals(quest.getActive()) && !rewardedQuests.contains(quest.getQuestCode())) {
                throw new IllegalStateException("Nhiệm vụ active thiếu phần thưởng mặc định: "
                    + quest.getQuestCode());
            }
        }
    }

    private void validateLevel(LevelRuleRow row) {
        if (row == null || row.getLevel() == null || row.getLevel() < 1
            || row.getMinExp() == null || row.getMinExp() < 0 || invalidText(row.getTitleKey(), 64)
            || (row.getFrameCode() != null && row.getFrameCode().length() > 32)) {
            throw new IllegalArgumentException("Quy tắc level không hợp lệ");
        }
    }

    private void validateQuest(QuestDefinitionRow row, long expReward, long ticketReward) {
        if (row == null || invalidCode(row.getQuestCode(), 48) || invalidCode(row.getEventType(), 48)
            || !PERIOD_TYPES.contains(row.getPeriodType()) || row.getTargetCount() == null
            || row.getTargetCount() < 1 || invalidText(row.getNameKey(), 64)
            || row.getSortNo() == null || row.getSortNo() < 0 || row.getActive() == null
            || expReward < 0 || ticketReward < 0 || expReward + ticketReward <= 0) {
            throw new IllegalArgumentException("Nhiệm vụ hoặc phần thưởng mặc định không hợp lệ");
        }
    }

    private void validateAbuse(int reviewScoreThreshold, TicketRiskRuleRow row) {
        boolean accountAge = row != null && "ACCOUNT_AGE_HOURS".equals(row.getMetricName());
        if (reviewScoreThreshold <= 0 || row == null || invalidCode(row.getRuleCode(), 48)
            || !ABUSE_METRICS.contains(row.getMetricName()) || row.getThresholdValue() == null
            || row.getThresholdValue() < 0 || row.getScore() == null || row.getScore() <= 0
            || row.getHardBlock() == null || (accountAge && row.getWindowMinutes() != null)
            || (!accountAge && (row.getWindowMinutes() == null || row.getWindowMinutes() <= 0))) {
            throw new IllegalArgumentException("Rule chống lạm dụng không hợp lệ");
        }
    }

    private GamificationPolicyBundleRow lockDraft(String version, long expectedVersion) {
        GamificationPolicyBundleRow bundle = requireBundle(mapper.lockBundle(version));
        requireState(bundle, expectedVersion, "DRAFT");
        return bundle;
    }

    private void requireState(GamificationPolicyBundleRow row, long expectedVersion,
                              String expectedStatus) {
        if (expectedVersion < 0 || !Objects.equals(row.getVersion(), expectedVersion)) {
            throw conflict();
        }
        if (!expectedStatus.equals(row.getStatus())) {
            throw new IllegalStateException("Policy không ở trạng thái " + expectedStatus);
        }
    }

    private String currentHash(String version) {
        return hasher.hash(mapper.selectCanonicalLines(version));
    }

    private void audit(long id, String event, String from, String to, long operatorId,
                       String reason, String beforeHash, String afterHash) {
        if (mapper.insertAudit(id, event, from, to, operatorId, reason, beforeHash, afterHash) != 1) {
            throw new IllegalStateException("Không thể ghi audit policy gamification");
        }
    }

    private GamificationPolicyBundleRow requireBundle(GamificationPolicyBundleRow row) {
        if (row == null) {
            throw new IllegalArgumentException("Policy gamification không tồn tại");
        }
        return row;
    }

    private String requireVersion(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!normalized.matches("[a-z0-9][a-z0-9._-]{0,31}")) {
            throw new IllegalArgumentException("Phiên bản policy không hợp lệ");
        }
        return normalized;
    }

    private String requireReason(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() < 10 || normalized.length() > 500) {
            throw new IllegalArgumentException("Lý do phải dài từ 10 đến 500 ký tự");
        }
        return normalized;
    }

    private void requireOperator(long operatorId) {
        if (operatorId <= 0) {
            throw new IllegalArgumentException("Quản trị viên không hợp lệ");
        }
    }

    private boolean invalidCode(String value, int maxLength) {
        return value == null || !value.matches("[A-Z0-9_]{2," + maxLength + "}");
    }

    private boolean invalidText(String value, int maxLength) {
        return value == null || value.isBlank() || value.length() > maxLength;
    }

    private GamificationConfigConflictException conflict() {
        return new GamificationConfigConflictException(
            "Policy đã được cập nhật đồng thời hoặc expectedVersion đã stale");
    }
}
