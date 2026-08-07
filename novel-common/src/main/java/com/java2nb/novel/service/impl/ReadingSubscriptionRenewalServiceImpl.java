package com.java2nb.novel.service.impl;

import com.java2nb.novel.mapper.ReadingSubscriptionMapper;
import com.java2nb.novel.mapper.ReadingSubscriptionRenewalMapper;
import com.java2nb.novel.service.subscription.ReadingSubscriptionPlanRow;
import com.java2nb.novel.service.subscription.ReadingSubscriptionProviderCharge;
import com.java2nb.novel.service.subscription.ReadingSubscriptionRenewalAdminAuditRow;
import com.java2nb.novel.service.subscription.ReadingSubscriptionRenewalAttemptRow;
import com.java2nb.novel.service.subscription.ReadingSubscriptionRenewalQueueItem;
import com.java2nb.novel.service.subscription.ReadingSubscriptionRenewalCycleRow;
import com.java2nb.novel.service.subscription.ReadingSubscriptionRenewalResult;
import com.java2nb.novel.service.subscription.ReadingSubscriptionRenewalService;
import com.java2nb.novel.service.subscription.ReadingSubscriptionRow;
import com.java2nb.novel.service.wallet.WalletLedgerService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReadingSubscriptionRenewalServiceImpl implements ReadingSubscriptionRenewalService {
    private static final int MAX_PRIMARY_ATTEMPTS = 3;
    private static final Set<String> ADMIN_QUEUE_STATUSES = Set.of(
        "PROCESSING", "PROVIDER_PENDING", "RETRY_WAIT", "FAILED", "GRACE_EXPIRED");

    private final ReadingSubscriptionRenewalMapper renewalMapper;
    private final ReadingSubscriptionMapper subscriptionMapper;
    private final WalletLedgerService walletLedgerService;

    @Override
    @Transactional(readOnly = true)
    public List<Long> listDueSubscriptionIds(Date now, int limit) {
        requireBatch(now, limit);
        return renewalMapper.selectDueSubscriptionIds(now, limit);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReadingSubscriptionRenewalResult prepareCycle(long subscriptionId, Date now,
                                                         ZoneId zoneId) {
        if (subscriptionId <= 0 || now == null || zoneId == null) {
            throw new IllegalArgumentException("Yêu cầu chuẩn bị cycle gia hạn không hợp lệ");
        }
        ReadingSubscriptionRow subscription = renewalMapper.lockDueSubscription(subscriptionId, now);
        if (subscription == null) {
            return ReadingSubscriptionRenewalResult.NOT_DUE;
        }
        ReadingSubscriptionPlanRow plan = subscriptionMapper.selectPlanById(subscription.getPlanId());
        if (plan == null || !"ACTIVE".equals(plan.getStatus())) {
            if (renewalMapper.markPriceConsentRequired(subscriptionId, subscription.getVersion(), now) != 1) {
                throw new IllegalStateException("Thuê bao đã được cập nhật đồng thời");
            }
            return ReadingSubscriptionRenewalResult.PRICE_CONSENT_REQUIRED;
        }
        boolean newPriceEffective = plan.getPlanVersion() > subscription.getAcceptedPlanVersion()
            && (plan.getPriceEffectiveAt() == null || !plan.getPriceEffectiveAt().after(now));
        if (newPriceEffective) {
            if (renewalMapper.markPriceConsentRequired(subscriptionId, subscription.getVersion(), now) != 1) {
                throw new IllegalStateException("Thuê bao đã được cập nhật đồng thời");
            }
            return ReadingSubscriptionRenewalResult.PRICE_CONSENT_REQUIRED;
        }
        Date periodStart = subscription.getCurrentPeriodEnd();
        if (periodStart == null) {
            throw new IllegalStateException("Thuê bao thiếu mốc kết thúc kỳ hiện tại");
        }
        ZonedDateTime start = periodStart.toInstant().atZone(zoneId);
        Date periodEnd = Date.from(start.plusMonths(subscription.getPeriodMonthsSnapshot()).toInstant());
        Date graceEndAt = Date.from(start.plusDays(7).toInstant());
        String idempotencyKey = "SUBSCRIPTION_RENEWAL:" + subscriptionId + ':' + periodStart.getTime();
        try {
            if (renewalMapper.insertCycle(subscription, periodStart, periodEnd, graceEndAt,
                idempotencyKey, now) != 1) {
                throw new IllegalStateException("Không thể tạo cycle gia hạn");
            }
        } catch (DuplicateKeyException ignored) {
            return ReadingSubscriptionRenewalResult.NOT_DUE;
        }
        return ReadingSubscriptionRenewalResult.CYCLE_CREATED;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> listDueCycleIds(Date now, int limit) {
        requireBatch(now, limit);
        return renewalMapper.selectDueCycleIds(now, limit);
    }

    @Override
    @Transactional(readOnly = true)
    public long countCyclesByStatus(String status) {
        if (!ADMIN_QUEUE_STATUSES.contains(status) && !"DUE".equals(status)) {
            throw new IllegalArgumentException("Trạng thái cycle không được phép thống kê");
        }
        return renewalMapper.countCyclesByStatus(status);
    }

    @Override
    @Transactional(readOnly = true)
    public long countPastDueSubscriptions() {
        return renewalMapper.countPastDueSubscriptions();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReadingSubscriptionRenewalResult processCycle(long cycleId, Date now) {
        ReadingSubscriptionRenewalCycleRow cycle = lockProcessableCycle(cycleId, now);
        if (cycle == null) {
            return ReadingSubscriptionRenewalResult.NOT_DUE;
        }
        ReadingSubscriptionRow subscription = renewalMapper.lockSubscription(cycle.getSubscriptionId());
        if (subscription == null || !Boolean.TRUE.equals(subscription.getAutoRenew())) {
            return expire(cycle, subscription, now);
        }
        if (!now.before(cycle.getGraceEndAt())) {
            return expire(cycle, subscription, now);
        }
        int attemptNo = cycle.getAttemptCount() + 1;
        String fundingSource = fundingSource(subscription, attemptNo);
        if (fundingSource == null) {
            if (!now.before(cycle.getGraceEndAt())) {
                return expire(cycle, subscription, now);
            }
            Date retryAt = cycle.getGraceEndAt();
            if (renewalMapper.scheduleRetry(cycleId, cycle.getVersion(),
                cycle.getAttemptCount(), retryAt) != 1) {
                throw new IllegalStateException("Cycle gia hạn đã được cập nhật đồng thời");
            }
            return ReadingSubscriptionRenewalResult.RETRY_SCHEDULED;
        }
        String requestHash = requestHash(cycle, attemptNo, fundingSource);
        if ("VNPAY_RECURRING".equals(fundingSource)) {
            if (cycle.getPriceVndSnapshot() == null || cycle.getPriceVndSnapshot() <= 0) {
                throw new IllegalStateException("Cycle gia hạn VNPAY thiếu giá VND hợp lệ");
            }
            String providerRequestId = "NPR" + cycleId + "A" + attemptNo;
            if (renewalMapper.insertProviderAttempt(cycleId, attemptNo,
                providerRequestId, requestHash, now) != 1
                || renewalMapper.markProviderProcessing(cycleId, cycle.getVersion(), attemptNo, now) != 1
                || renewalMapper.markSubscriptionPastDue(subscription.getId(),
                    subscription.getVersion()) != 1) {
                throw new IllegalStateException("Không thể claim lần gia hạn VNPAY");
            }
            return ReadingSubscriptionRenewalResult.PROVIDER_CLAIMED;
        }
        if (!"WALLET_XU".equals(fundingSource) || cycle.getPriceXuSnapshot() == null) {
            throw new IllegalStateException("Nguồn hoặc giá gia hạn cycle không hợp lệ");
        }
        if (renewalMapper.insertAttempt(cycleId, attemptNo, fundingSource,
            "STARTED", requestHash, now) != 1) {
            throw new IllegalStateException("Không thể ghi lần thử gia hạn");
        }
        String ledgerKey = cycle.getIdempotencyKey() + ":WALLET_XU";
        walletLedgerService.chargeReaderSubscription(cycle.getUserId(),
            cycle.getPriceXuSnapshot(), Long.toString(cycleId), ledgerKey);
        if (renewalMapper.markAttemptSettled(cycleId, attemptNo, ledgerKey, now) != 1
            || renewalMapper.markCycleSettled(cycleId, cycle.getVersion(), fundingSource,
                ledgerKey, now) != 1
            || renewalMapper.advanceSubscription(cycle, subscription.getVersion()) != 1) {
            throw new IllegalStateException("Không thể hoàn tất cycle gia hạn đã thanh toán");
        }
        return ReadingSubscriptionRenewalResult.SETTLED;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReadingSubscriptionRenewalResult recordWalletFailure(long cycleId, Date now) {
        ReadingSubscriptionRenewalCycleRow cycle = lockProcessableCycle(cycleId, now);
        if (cycle == null) {
            return ReadingSubscriptionRenewalResult.NOT_DUE;
        }
        ReadingSubscriptionRow subscription = renewalMapper.lockSubscription(cycle.getSubscriptionId());
        if (subscription == null || !Boolean.TRUE.equals(subscription.getAutoRenew())) {
            return expire(cycle, subscription, now);
        }
        int attemptNo = cycle.getAttemptCount() + 1;
        String fundingSource = fundingSource(subscription, attemptNo);
        if (!"WALLET_XU".equals(fundingSource)) {
            throw new IllegalStateException("Chỉ ghi lỗi ví cho lần thử nguồn WALLET_XU");
        }
        if (renewalMapper.insertAttempt(cycleId, attemptNo, fundingSource,
            "STARTED", requestHash(cycle, attemptNo, fundingSource), now) != 1
            || renewalMapper.markAttemptFailed(cycleId, attemptNo,
                "Số dư Xu không đủ", now) != 1) {
            throw new IllegalStateException("Không thể ghi lỗi gia hạn bằng ví Xu");
        }
        Date nextAttemptAt = nextAttemptAt(cycle, subscription, attemptNo, now);
        if (renewalMapper.scheduleRetry(cycleId, cycle.getVersion(), attemptNo,
            nextAttemptAt) != 1
            || renewalMapper.markSubscriptionPastDue(subscription.getId(),
                subscription.getVersion()) != 1) {
            throw new IllegalStateException("Không thể lên lịch thử lại gia hạn");
        }
        return ReadingSubscriptionRenewalResult.RETRY_SCHEDULED;
    }

    @Override
    @Transactional(readOnly = true)
    public ReadingSubscriptionProviderCharge getProviderCharge(long cycleId) {
        if (cycleId <= 0) {
            throw new IllegalArgumentException("Cycle gia hạn không hợp lệ");
        }
        return renewalMapper.selectProviderCharge(cycleId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReadingSubscriptionRenewalResult settleProviderCharge(long cycleId, int attemptNo,
                                                                  String providerTransactionId,
                                                                  Date now) {
        requireProviderResult(cycleId, attemptNo, providerTransactionId, now);
        ReadingSubscriptionRenewalCycleRow cycle = lockClaimedProviderCycle(cycleId, attemptNo);
        if (cycle == null) {
            return ReadingSubscriptionRenewalResult.NOT_DUE;
        }
        ReadingSubscriptionRow subscription = renewalMapper.lockSubscription(cycle.getSubscriptionId());
        if (subscription == null || !Boolean.TRUE.equals(subscription.getAutoRenew())) {
            throw new IllegalStateException("Thuê bao không còn hợp lệ sau khi VNPAY đã settlement");
        }
        if (renewalMapper.markProviderAttemptSettled(cycleId, attemptNo,
            providerTransactionId, now) != 1
            || renewalMapper.markProviderCycleSettled(cycleId, cycle.getVersion(),
                providerTransactionId, now) != 1
            || renewalMapper.advanceSubscription(cycle, subscription.getVersion()) != 1) {
            throw new IllegalStateException("Không thể hoàn tất cycle VNPAY đã settlement");
        }
        return ReadingSubscriptionRenewalResult.SETTLED;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReadingSubscriptionRenewalResult recordProviderFailure(long cycleId, int attemptNo,
                                                                   String responseCode, Date now) {
        String code = normalizeProviderCode(responseCode);
        ReadingSubscriptionRenewalCycleRow cycle = lockClaimedProviderCycle(cycleId, attemptNo);
        if (cycle == null) {
            return ReadingSubscriptionRenewalResult.NOT_DUE;
        }
        ReadingSubscriptionRow subscription = renewalMapper.lockSubscription(cycle.getSubscriptionId());
        if (subscription == null || !Boolean.TRUE.equals(subscription.getAutoRenew())) {
            throw new IllegalStateException("Thuê bao không còn hợp lệ khi ghi lỗi VNPAY");
        }
        Date nextAttemptAt = nextAttemptAt(cycle, subscription, attemptNo, now);
        if (renewalMapper.markProviderAttemptFailed(cycleId, attemptNo, code, now) != 1
            || renewalMapper.scheduleProviderRetry(cycleId, cycle.getVersion(), nextAttemptAt) != 1) {
            throw new IllegalStateException("Không thể lên lịch thử lại VNPAY");
        }
        return ReadingSubscriptionRenewalResult.RETRY_SCHEDULED;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReadingSubscriptionRenewalResult recordProviderPending(long cycleId, int attemptNo,
                                                                   String responseCode, Date now) {
        String code = normalizeProviderCode(responseCode);
        ReadingSubscriptionRenewalCycleRow cycle = lockClaimedProviderCycle(cycleId, attemptNo);
        if (cycle == null) {
            return ReadingSubscriptionRenewalResult.NOT_DUE;
        }
        if (renewalMapper.markProviderAttemptUnknown(cycleId, attemptNo, code, now) != 1
            || renewalMapper.markProviderPending(cycleId, cycle.getVersion(), attemptNo, now) != 1) {
            throw new IllegalStateException("Không thể chuyển cycle sang chờ tra soát VNPAY");
        }
        return ReadingSubscriptionRenewalResult.PROVIDER_PENDING;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReadingSubscriptionRenewalQueueItem> listRenewalQueue(String status, int limit) {
        String normalized = status == null ? "" : status.trim().toUpperCase();
        if (!ADMIN_QUEUE_STATUSES.contains(normalized) || limit < 1 || limit > 200) {
            throw new IllegalArgumentException("Bộ lọc queue gia hạn không hợp lệ");
        }
        return renewalMapper.selectRenewalQueue(normalized, limit);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReadingSubscriptionRenewalAttemptRow> listRenewalAttempts(long cycleId, int limit) {
        requireAdminHistory(cycleId, limit);
        return renewalMapper.selectRenewalAttempts(cycleId, limit);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReadingSubscriptionRenewalAdminAuditRow> listRenewalAdminAudits(long cycleId,
                                                                                int limit) {
        requireAdminHistory(cycleId, limit);
        return renewalMapper.selectRenewalAdminAudits(cycleId, limit);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReadingSubscriptionRenewalResult adminScheduleRetry(long cycleId, long expectedVersion,
                                                                long operatorId, String reason,
                                                                Date now) {
        String normalizedReason = reason == null ? "" : reason.trim();
        if (cycleId <= 0 || expectedVersion < 0 || operatorId <= 0 || now == null
            || normalizedReason.length() < 8 || normalizedReason.length() > 500) {
            throw new IllegalArgumentException("Yêu cầu retry gia hạn không hợp lệ");
        }
        ReadingSubscriptionRenewalCycleRow cycle = renewalMapper.lockCycle(cycleId);
        if (cycle == null || !"RETRY_WAIT".equals(cycle.getStatus())
            || cycle.getVersion() == null || cycle.getVersion() != expectedVersion) {
            throw new IllegalStateException("Cycle không còn ở trạng thái cho phép retry");
        }
        if (renewalMapper.adminScheduleRetry(cycleId, expectedVersion, now) != 1
            || renewalMapper.insertRenewalAdminAudit(cycleId, cycle.getUserId(), operatorId,
                "RETRY_SCHEDULED", "RETRY_WAIT", "RETRY_WAIT", normalizedReason, now) != 1) {
            throw new IllegalStateException("Không thể lên lịch retry cycle gia hạn");
        }
        return ReadingSubscriptionRenewalResult.RETRY_SCHEDULED;
    }

    private ReadingSubscriptionRenewalCycleRow lockProcessableCycle(long cycleId, Date now) {
        if (cycleId <= 0 || now == null) {
            throw new IllegalArgumentException("Cycle gia hạn không hợp lệ");
        }
        ReadingSubscriptionRenewalCycleRow cycle = renewalMapper.lockCycle(cycleId);
        if (cycle == null || !List.of("DUE", "RETRY_WAIT").contains(cycle.getStatus())
            || cycle.getNextAttemptAt().after(now)) {
            return null;
        }
        return cycle;
    }

    private ReadingSubscriptionRenewalCycleRow lockClaimedProviderCycle(long cycleId, int attemptNo) {
        if (cycleId <= 0 || attemptNo < 1 || attemptNo > 10) {
            throw new IllegalArgumentException("Kết quả gia hạn provider không hợp lệ");
        }
        ReadingSubscriptionRenewalCycleRow cycle = renewalMapper.lockCycle(cycleId);
        if (cycle == null || !"PROCESSING".equals(cycle.getStatus())
            || cycle.getAttemptCount() == null || cycle.getAttemptCount() != attemptNo) {
            return null;
        }
        return cycle;
    }

    private String fundingSource(ReadingSubscriptionRow subscription, int attemptNo) {
        if (attemptNo <= MAX_PRIMARY_ATTEMPTS) {
            return subscription.getPrimaryFundingSource();
        }
        return subscription.getFallbackFundingSource();
    }

    private Date nextAttemptAt(ReadingSubscriptionRenewalCycleRow cycle,
                               ReadingSubscriptionRow subscription,
                               int attemptNo, Date now) {
        if (attemptNo == 1) {
            return new Date(now.getTime() + 24L * 60 * 60 * 1000);
        }
        if (attemptNo == 2) {
            return new Date(now.getTime() + 48L * 60 * 60 * 1000);
        }
        if (attemptNo == MAX_PRIMARY_ATTEMPTS
            && subscription.getFallbackFundingSource() != null) {
            return now;
        }
        return cycle.getGraceEndAt();
    }

    private ReadingSubscriptionRenewalResult expire(
        ReadingSubscriptionRenewalCycleRow cycle, ReadingSubscriptionRow subscription, Date now) {
        if (renewalMapper.expireCycle(cycle.getId(), cycle.getVersion()) != 1) {
            throw new IllegalStateException("Cycle gia hạn đã được cập nhật đồng thời");
        }
        if (subscription != null && Boolean.TRUE.equals(subscription.getAutoRenew())
            && List.of("ACTIVE", "PAST_DUE").contains(subscription.getStatus())
            && renewalMapper.expireSubscription(subscription.getId(),
                subscription.getVersion(), now) != 1) {
            throw new IllegalStateException("Thuê bao đã được cập nhật đồng thời");
        }
        return ReadingSubscriptionRenewalResult.GRACE_EXPIRED;
    }

    private String requestHash(ReadingSubscriptionRenewalCycleRow cycle, int attemptNo,
                               String fundingSource) {
        String canonical = cycle.getId() + "|" + cycle.getSubscriptionId() + "|"
            + attemptNo + "|" + fundingSource + "|" + cycle.getPriceVndSnapshot() + "|"
            + cycle.getPriceXuSnapshot() + "|" + cycle.getPeriodStart().getTime();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM không hỗ trợ SHA-256", exception);
        }
    }

    private void requireBatch(Date now, int limit) {
        if (now == null || limit < 1 || limit > 500) {
            throw new IllegalArgumentException("Lô gia hạn không hợp lệ");
        }
    }

    private void requireProviderResult(long cycleId, int attemptNo, String reference, Date now) {
        if (cycleId <= 0 || attemptNo < 1 || attemptNo > 10 || now == null
            || reference == null || !reference.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("Settlement provider không hợp lệ");
        }
    }

    private String normalizeProviderCode(String responseCode) {
        if (responseCode == null || !responseCode.matches("[A-Za-z0-9_-]{1,32}")) {
            return "UNKNOWN";
        }
        return responseCode;
    }

    private void requireAdminHistory(long cycleId, int limit) {
        if (cycleId <= 0 || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Yêu cầu lịch sử gia hạn không hợp lệ");
        }
    }
}
