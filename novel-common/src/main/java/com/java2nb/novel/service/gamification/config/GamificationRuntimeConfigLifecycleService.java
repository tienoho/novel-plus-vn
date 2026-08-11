package com.java2nb.novel.service.gamification.config;

import com.java2nb.novel.mapper.GamificationRuntimeConfigMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Objects;

@Service
public class GamificationRuntimeConfigLifecycleService {

    private final GamificationRuntimeConfigMapper mapper;
    private final GamificationConfigValidator validator;
    private final GamificationConfigHasher hasher;
    private final GamificationSecretReadiness secretReadiness;
    private final Clock clock;

    @Autowired
    public GamificationRuntimeConfigLifecycleService(GamificationRuntimeConfigMapper mapper,
                                                      GamificationConfigValidator validator,
                                                      GamificationConfigHasher hasher,
                                                      GamificationSecretReadiness secretReadiness) {
        this(mapper, validator, hasher, secretReadiness, Clock.systemUTC());
    }

    GamificationRuntimeConfigLifecycleService(GamificationRuntimeConfigMapper mapper,
                                               GamificationConfigValidator validator,
                                               GamificationConfigHasher hasher,
                                               GamificationSecretReadiness secretReadiness,
                                               Clock clock) {
        this.mapper = mapper;
        this.validator = validator;
        this.hasher = hasher;
        this.secretReadiness = secretReadiness;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public GamificationRuntimeConfigRow getActive() {
        return mapper.selectActive();
    }

    @Transactional(readOnly = true)
    public GamificationRuntimeConfigRow get(long id) {
        return requireExisting(mapper.selectById(id));
    }

    @Transactional(readOnly = true)
    public List<GamificationRuntimeConfigRow> history(int limit) {
        return mapper.selectHistory(Math.max(1, Math.min(limit, 200)));
    }

    @Transactional(readOnly = true)
    public GamificationConfigDiffView diff(long id) {
        GamificationRuntimeConfigRow active = requireExisting(mapper.selectActive());
        GamificationRuntimeConfigRow target = requireExisting(mapper.selectById(id));
        GamificationConfigDiff diff = GamificationConfigDiff.between(
            active.getSnapshot(), target.getSnapshot());
        return GamificationConfigDiffView.of(diff, active.getSnapshot(), target.getSnapshot(),
            earliestEffectiveAt(target, diff.activationClass()).toEpochMilli());
    }

    @Transactional(rollbackFor = Exception.class)
    public GamificationRuntimeConfigRow cloneActive(long operatorId, String reason) {
        GamificationRuntimeConfigRow active = requireExisting(mapper.selectActive());
        return createDraft(active.getSnapshot(), active.getId(), null, operatorId, reason);
    }

    @Transactional(rollbackFor = Exception.class)
    public GamificationRuntimeConfigRow importSnapshot(GamificationConfigSnapshot snapshot,
                                                        String sourceHash, long operatorId,
                                                        String reason) {
        requireSourceHash(sourceHash);
        GamificationRuntimeConfigRow existing = mapper.selectBySourceHash(sourceHash);
        if (existing != null) {
            if (!Objects.equals(existing.getConfigHash(), hasher.hash(snapshot))) {
                throw new IllegalStateException("Source hash đã tồn tại với nội dung cấu hình khác");
            }
            return existing;
        }
        return createDraft(snapshot, null, sourceHash, operatorId, reason);
    }

    @Transactional(rollbackFor = Exception.class)
    public GamificationRuntimeConfigRow saveDraft(long id, long expectedVersion,
                                                   GamificationConfigSnapshot snapshot,
                                                   long operatorId, String reason) {
        requireOperator(operatorId);
        String normalizedReason = requireReason(reason);
        GamificationRuntimeConfigRow row = requireExisting(mapper.lockById(id));
        requireVersion(row, expectedVersion);
        requireStatus(row, "DRAFT");
        validate(snapshot);
        snapshot = snapshot.toBuilder().runtimeRevision(row.getRevisionNo()).build();
        row.setSnapshot(snapshot);
        row.setConfigHash(hasher.hash(snapshot));
        row.setChangeReason(normalizedReason);
        if (mapper.saveDraft(row, expectedVersion) != 1) {
            throw conflict();
        }
        audit(row.getId(), "SAVED", "DRAFT", "DRAFT", operatorId, normalizedReason,
            null, row.getConfigHash(), null);
        return requireExisting(mapper.selectById(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public GamificationRuntimeConfigRow submit(long id, long expectedVersion,
                                                long operatorId, String reason) {
        requireOperator(operatorId);
        String normalizedReason = requireReason(reason);
        GamificationRuntimeConfigRow row = requireExisting(mapper.lockById(id));
        requireVersion(row, expectedVersion);
        requireStatus(row, "DRAFT");
        GamificationConfigSnapshot snapshot = row.getSnapshot();
        validate(snapshot);
        if (!mapper.isPolicyPublished(snapshot.getPolicyVersion())) {
            throw new IllegalStateException("Policy được tham chiếu chưa ở trạng thái PUBLISHED");
        }
        validatePublishedPolicyDependencies(snapshot);
        GamificationRuntimeConfigRow active = requireExisting(mapper.selectActive());
        GamificationConfigDiff diff = GamificationConfigDiff.between(active.getSnapshot(), snapshot);
        Date now = Date.from(clock.instant());
        if (mapper.submit(id, expectedVersion, diff.activationClass().name(), diff.highRisk(),
            operatorId, now, normalizedReason) != 1) {
            throw conflict();
        }
        String diffJson = changedKeysJson(diff.changedKeys());
        audit(id, "SUBMITTED", "DRAFT", "PENDING_APPROVAL", operatorId, normalizedReason,
            active.getConfigHash(), row.getConfigHash(), diffJson);
        row.setStatus("PENDING_APPROVAL");
        row.setActivationClass(diff.activationClass().name());
        row.setHighRisk(diff.highRisk());
        row.setVersion(expectedVersion + 1);
        return row;
    }

    @Transactional(rollbackFor = Exception.class)
    public GamificationRuntimeConfigRow approve(long id, long expectedVersion,
                                                 long operatorId, String reason) {
        requireOperator(operatorId);
        String normalizedReason = requireReason(reason);
        GamificationRuntimeConfigRow row = requireExisting(mapper.lockById(id));
        requireVersion(row, expectedVersion);
        requireStatus(row, "PENDING_APPROVAL");
        if (Boolean.TRUE.equals(row.getHighRisk()) && Objects.equals(row.getCreatedBy(), operatorId)) {
            throw new SecurityException("Người tạo không được tự phê duyệt thay đổi rủi ro cao");
        }
        Date now = Date.from(clock.instant());
        if (mapper.approve(id, expectedVersion, operatorId, now) != 1) {
            throw conflict();
        }
        audit(id, "APPROVED", "PENDING_APPROVAL", "APPROVED", operatorId,
            normalizedReason, row.getConfigHash(), row.getConfigHash(), null);
        row.setStatus("APPROVED");
        row.setVersion(expectedVersion + 1);
        return row;
    }

    @Transactional(rollbackFor = Exception.class)
    public GamificationRuntimeConfigRow reject(long id, long expectedVersion,
                                                long operatorId, String reason) {
        return terminalTransition(id, expectedVersion, operatorId, reason,
            "PENDING_APPROVAL", "REJECTED", "REJECTED");
    }

    @Transactional(rollbackFor = Exception.class)
    public GamificationRuntimeConfigRow schedule(long id, long expectedVersion,
                                                  long operatorId, String reason,
                                                  Date effectiveAt) {
        requireOperator(operatorId);
        String normalizedReason = requireReason(reason);
        if (effectiveAt == null || effectiveAt.before(Date.from(clock.instant()))) {
            throw new IllegalArgumentException("Thời điểm kích hoạt không được nằm trong quá khứ");
        }
        GamificationRuntimeConfigRow row = requireExisting(mapper.lockById(id));
        requireVersion(row, expectedVersion);
        requireStatus(row, "APPROVED");
        GamificationActivationClass activationClass = GamificationActivationClass.valueOf(
            row.getActivationClass());
        Instant earliest = earliestEffectiveAt(row, activationClass);
        if (effectiveAt.toInstant().isBefore(earliest)) {
            throw new IllegalArgumentException("Thời điểm kích hoạt sớm hơn ranh giới "
                + activationClass + ": " + earliest);
        }
        if (mapper.schedule(id, expectedVersion, operatorId, effectiveAt, normalizedReason) != 1) {
            throw conflict();
        }
        audit(id, "SCHEDULED", "APPROVED", "SCHEDULED", operatorId, normalizedReason,
            row.getConfigHash(), row.getConfigHash(), null);
        row.setStatus("SCHEDULED");
        row.setEffectiveAt(effectiveAt);
        row.setVersion(expectedVersion + 1);
        return row;
    }

    @Transactional(rollbackFor = Exception.class)
    public GamificationRuntimeConfigRow cancel(long id, long expectedVersion,
                                                long operatorId, String reason) {
        return terminalTransition(id, expectedVersion, operatorId, reason,
            "SCHEDULED", "CANCELLED", "CANCELLED");
    }

    @Transactional(rollbackFor = Exception.class)
    public GamificationRuntimeConfigRow activateDue(long operatorId) {
        requireOperator(operatorId);
        Date now = Date.from(clock.instant());
        GamificationRuntimeConfigRow scheduled = mapper.lockDueScheduled(now);
        if (scheduled == null) {
            return null;
        }
        GamificationConfigSnapshot snapshot = scheduled.getSnapshot();
        validate(snapshot);
        if (!mapper.isPolicyPublished(snapshot.getPolicyVersion())) {
            throw new IllegalStateException("Policy được tham chiếu không còn ở trạng thái PUBLISHED");
        }
        validatePublishedPolicyDependencies(snapshot);
        GamificationRuntimeConfigRow active = requireExisting(mapper.selectActive());
        if (mapper.archiveActive(active.getId(), active.getVersion(), now) != 1) {
            throw conflict();
        }
        if (mapper.activateScheduled(scheduled.getId(), scheduled.getVersion(), operatorId, now) != 1) {
            throw conflict();
        }
        audit(active.getId(), "ARCHIVED", "ACTIVE", "ARCHIVED", operatorId,
            "Kích hoạt revision " + scheduled.getRevisionCode(), active.getConfigHash(),
            active.getConfigHash(), null);
        audit(scheduled.getId(), "ACTIVATED", "SCHEDULED", "ACTIVE", operatorId,
            scheduled.getChangeReason(), scheduled.getConfigHash(), scheduled.getConfigHash(), null);
        scheduled.setStatus("ACTIVE");
        scheduled.setActivatedAt(now);
        scheduled.setVersion(scheduled.getVersion() + 1);
        return scheduled;
    }

    @Transactional(rollbackFor = Exception.class)
    public GamificationRuntimeConfigRow rollbackFrom(long sourceRevisionId, long operatorId,
                                                      String reason) {
        GamificationRuntimeConfigRow source = requireExisting(mapper.selectById(sourceRevisionId));
        return createDraft(source.getSnapshot(), source.getId(), null, operatorId, reason);
    }

    private GamificationRuntimeConfigRow terminalTransition(long id, long expectedVersion,
                                                             long operatorId, String reason,
                                                             String fromStatus, String toStatus,
                                                             String eventType) {
        requireOperator(operatorId);
        String normalizedReason = requireReason(reason);
        GamificationRuntimeConfigRow row = requireExisting(mapper.lockById(id));
        requireVersion(row, expectedVersion);
        requireStatus(row, fromStatus);
        int changed = "REJECTED".equals(toStatus)
            ? mapper.reject(id, expectedVersion, operatorId, normalizedReason)
            : mapper.cancel(id, expectedVersion, operatorId, normalizedReason);
        if (changed != 1) {
            throw conflict();
        }
        audit(id, eventType, fromStatus, toStatus, operatorId, normalizedReason,
            row.getConfigHash(), row.getConfigHash(), null);
        row.setStatus(toStatus);
        row.setVersion(expectedVersion + 1);
        return row;
    }

    private GamificationRuntimeConfigRow createDraft(GamificationConfigSnapshot snapshot,
                                                      Long sourceRevisionId, String sourceHash,
                                                      long operatorId, String reason) {
        requireOperator(operatorId);
        String normalizedReason = requireReason(reason);
        validate(snapshot);
        long revision = mapper.selectNextRevisionNo();
        GamificationConfigSnapshot revisioned = snapshot.toBuilder().runtimeRevision(revision).build();
        GamificationRuntimeConfigRow row = new GamificationRuntimeConfigRow();
        row.setRevisionNo(revision);
        row.setRevisionCode("v" + revision + "-draft");
        row.setSourceRevisionId(sourceRevisionId);
        row.setSourceHash(sourceHash);
        row.setStatus("DRAFT");
        row.setActivationClass("IMMEDIATE");
        row.setHighRisk(false);
        row.setSnapshot(revisioned);
        row.setConfigHash(hasher.hash(revisioned));
        row.setCreatedBy(operatorId);
        row.setChangeReason(normalizedReason);
        row.setVersion(0L);
        if (mapper.insertDraft(row) != 1 || row.getId() == null) {
            throw new IllegalStateException("Không thể tạo draft cấu hình gamification");
        }
        audit(row.getId(), "CREATED", null, "DRAFT", operatorId, normalizedReason,
            null, row.getConfigHash(), null);
        return row;
    }

    private void validate(GamificationConfigSnapshot snapshot) {
        List<String> errors = validator.validate(snapshot, snapshot == null ? null
            : snapshot.getVoteIpHashKeyId(), snapshot != null
            && secretReadiness.isReady(snapshot.getVoteIpHashKeyId()));
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Cấu hình gamification không hợp lệ: "
                + String.join(", ", errors));
        }
    }

    private void validatePublishedPolicyDependencies(GamificationConfigSnapshot snapshot) {
        if (snapshot.isQuestEnabled()) {
            Integer readingTarget = mapper.selectActiveReadingQuestTarget(snapshot.getPolicyVersion());
            if (readingTarget != null
                && snapshot.getQuestHeartbeatMaxMinutesPerDay() < readingTarget) {
                throw new IllegalArgumentException(
                    "Trần heartbeat theo ngày thấp hơn mục tiêu nhiệm vụ đọc: " + readingTarget);
            }
        }
        if (snapshot.isRewardEnabled()) {
            if (!mapper.hasPublishedPublicPolicy(snapshot.getPolicyVersion())) {
                throw new IllegalStateException(
                    "Bật quỹ thưởng yêu cầu luật chơi công khai đã phát hành");
            }
            if (!mapper.hasApprovedRewardCampaign(snapshot.getPolicyVersion())) {
                throw new IllegalStateException(
                    "Bật quỹ thưởng yêu cầu campaign đã duyệt, đang bật và có ngân sách");
            }
        }
    }

    private Instant earliestEffectiveAt(GamificationRuntimeConfigRow row,
                                        GamificationActivationClass activationClass) {
        Instant now = clock.instant();
        if (activationClass == GamificationActivationClass.NEXT_DAY) {
            ZoneId zone = ZoneId.of(row.getZoneId());
            return now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant();
        }
        if (activationClass == GamificationActivationClass.NEXT_SEASON) {
            Date openSeasonEnd = mapper.selectLatestOpenSeasonEnd();
            if (openSeasonEnd != null && openSeasonEnd.toInstant().isAfter(now)) {
                return openSeasonEnd.toInstant();
            }
        }
        return now;
    }

    private void audit(long id, String event, String from, String to, long actor, String reason,
                       String beforeHash, String afterHash, String diffJson) {
        if (mapper.insertAudit(id, event, from, to, actor, reason, beforeHash, afterHash,
            diffJson) != 1) {
            throw new IllegalStateException("Không thể ghi audit cấu hình gamification");
        }
    }

    private String changedKeysJson(Iterable<String> keys) {
        StringBuilder value = new StringBuilder("{\"changedKeys\":[");
        boolean first = true;
        for (String key : keys) {
            if (!first) {
                value.append(',');
            }
            value.append('\"').append(key).append('\"');
            first = false;
        }
        return value.append("]}").toString();
    }

    private GamificationRuntimeConfigRow requireExisting(GamificationRuntimeConfigRow row) {
        if (row == null) {
            throw new IllegalArgumentException("Revision cấu hình gamification không tồn tại");
        }
        return row;
    }

    private void requireVersion(GamificationRuntimeConfigRow row, long expectedVersion) {
        if (expectedVersion < 0 || !Objects.equals(row.getVersion(), expectedVersion)) {
            throw conflict();
        }
    }

    private void requireStatus(GamificationRuntimeConfigRow row, String expected) {
        if (!expected.equals(row.getStatus())) {
            throw new IllegalStateException("Revision không ở trạng thái " + expected);
        }
    }

    private void requireOperator(long operatorId) {
        if (operatorId <= 0) {
            throw new IllegalArgumentException("Quản trị viên không hợp lệ");
        }
    }

    private String requireReason(String reason) {
        String normalized = reason == null ? "" : reason.trim();
        if (normalized.length() < 10 || normalized.length() > 500) {
            throw new IllegalArgumentException("Lý do phải dài từ 10 đến 500 ký tự");
        }
        return normalized;
    }

    private void requireSourceHash(String sourceHash) {
        if (sourceHash == null || !sourceHash.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException("Source hash không hợp lệ");
        }
    }

    private GamificationConfigConflictException conflict() {
        return new GamificationConfigConflictException(
            "Revision đã được cập nhật đồng thời hoặc expectedVersion đã stale");
    }
}
