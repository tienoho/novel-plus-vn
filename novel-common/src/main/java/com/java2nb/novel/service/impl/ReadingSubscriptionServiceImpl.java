package com.java2nb.novel.service.impl;

import com.java2nb.novel.mapper.ReadingSubscriptionMapper;
import com.java2nb.novel.mapper.ReadingSubscriptionMandateMapper;
import com.java2nb.novel.mapper.ReadingSubscriptionPurchaseMapper;
import com.java2nb.novel.service.entitlement.ReadingTicketGrantCommand;
import com.java2nb.novel.service.entitlement.ReadingTicketPostResult;
import com.java2nb.novel.service.entitlement.ReadingTicketService;
import com.java2nb.novel.service.subscription.ReadingSubscriptionActivationCommand;
import com.java2nb.novel.service.subscription.ReadingSubscriptionCheckoutOptions;
import com.java2nb.novel.service.subscription.ReadingSubscriptionGrantResult;
import com.java2nb.novel.service.subscription.ReadingSubscriptionGrantStatus;
import com.java2nb.novel.service.subscription.ReadingSubscriptionMandateAdminAuditRow;
import com.java2nb.novel.service.subscription.ReadingSubscriptionMandateAdminResult;
import com.java2nb.novel.service.subscription.ReadingSubscriptionMandateQueueItem;
import com.java2nb.novel.service.subscription.ReadingSubscriptionMandateRow;
import com.java2nb.novel.service.subscription.ReadingSubscriptionPlanRow;
import com.java2nb.novel.service.subscription.ReadingSubscriptionPlanCommand;
import com.java2nb.novel.service.subscription.ReadingSubscriptionPeriodGrantRow;
import com.java2nb.novel.service.subscription.ReadingSubscriptionRow;
import com.java2nb.novel.service.subscription.ReadingSubscriptionPurchaseActivationCommand;
import com.java2nb.novel.service.subscription.ReadingSubscriptionPurchasePage;
import com.java2nb.novel.service.subscription.ReadingSubscriptionPurchaseReviewResult;
import com.java2nb.novel.service.subscription.ReadingSubscriptionPurchaseRow;
import com.java2nb.novel.service.subscription.ReadingSubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ReadingSubscriptionServiceImpl implements ReadingSubscriptionService {
    private final ReadingSubscriptionMapper mapper;
    private final ReadingSubscriptionMandateMapper mandateMapper;
    private final ReadingSubscriptionPurchaseMapper purchaseMapper;
    private final ReadingTicketService ticketService;

    @Override
    @Transactional(readOnly = true)
    public List<ReadingSubscriptionPlanRow> listActivePlans() {
        return mapper.selectActivePlans();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReadingSubscriptionPlanRow> listPlansForAdmin() {
        return mapper.selectAllPlans();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReadingSubscriptionPlanRow createPlan(ReadingSubscriptionPlanCommand command) {
        Objects.requireNonNull(command, "Thiếu cấu hình gói thuê bao");
        if (mapper.insertPlan(command) != 1) {
            throw new IllegalStateException("Không thể tạo gói thuê bao");
        }
        ReadingSubscriptionPlanRow created = mapper.selectPlanByCode(command.planCode());
        if (created == null) {
            throw new IllegalStateException("Không đọc được gói thuê bao vừa tạo");
        }
        return created;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReadingSubscriptionPlanRow updatePlan(long planId, long expectedVersion,
                                                 ReadingSubscriptionPlanCommand command) {
        Objects.requireNonNull(command, "Thiếu cấu hình gói thuê bao");
        ReadingSubscriptionPlanRow current = requirePlanVersion(planId, expectedVersion);
        if (!Objects.equals(current.getPlanCode(), command.planCode())) {
            throw new IllegalArgumentException("Không được đổi mã gói thuê bao");
        }
        if ("RETIRED".equals(current.getStatus())) {
            throw new IllegalStateException("Không được sửa gói thuê bao RETIRED");
        }
        if (mapper.updatePlan(planId, expectedVersion, command) != 1) {
            throw new IllegalStateException("Gói thuê bao đã được cập nhật đồng thời");
        }
        return mapper.selectPlanById(planId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReadingSubscriptionPlanRow changePlanStatus(long planId, long expectedVersion,
                                                       String status) {
        String target = status == null ? "" : status.trim().toUpperCase(java.util.Locale.ROOT);
        if (!"ACTIVE".equals(target) && !"RETIRED".equals(target)) {
            throw new IllegalArgumentException("Trạng thái gói thuê bao không hợp lệ");
        }
        ReadingSubscriptionPlanRow current = requirePlanVersion(planId, expectedVersion);
        if ("RETIRED".equals(current.getStatus())) {
            throw new IllegalStateException("Gói thuê bao RETIRED là trạng thái kết thúc");
        }
        if ("ACTIVE".equals(target)
            && (current.getPriceVnd() == null || current.getPriceVnd() < 1_000)) {
            throw new IllegalStateException("Gói thuê bao chưa có giá VND hợp lệ");
        }
        if (Objects.equals(current.getStatus(), target)) {
            return current;
        }
        if (mapper.updatePlanStatus(planId, expectedVersion, target) != 1) {
            throw new IllegalStateException("Gói thuê bao đã được cập nhật đồng thời");
        }
        return mapper.selectPlanById(planId);
    }

    @Override
    @Transactional(readOnly = true)
    public ReadingSubscriptionRow getCurrentSubscription(long userId) {
        if (userId <= 0) {
            throw new IllegalArgumentException("User thuê bao không hợp lệ");
        }
        return mapper.selectCurrentSubscriptionByUserId(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReadingSubscriptionRow updateRenewalSettings(
        long userId, long subscriptionId, long expectedVersion,
        ReadingSubscriptionCheckoutOptions options) {
        Objects.requireNonNull(options, "Thiếu cấu hình tự gia hạn");
        ReadingSubscriptionRow subscription = requireOwnedSubscription(
            userId, subscriptionId, expectedVersion);
        ReadingSubscriptionPlanRow plan = mapper.selectPlanById(subscription.getPlanId());
        if (plan == null || !"ACTIVE".equals(plan.getStatus())) {
            throw new IllegalStateException("Gói thuê bao không còn hoạt động");
        }
        if (options.autoRenew()) {
            if (!Objects.equals(options.acceptedPlanVersion(),
                    subscription.getAcceptedPlanVersion())
                || !Objects.equals(plan.getPlanVersion(), subscription.getAcceptedPlanVersion())) {
                throw new IllegalStateException("Cần chấp nhận giá hiện hành trước khi bật tự gia hạn");
            }
            requireFundingAvailable(userId, plan, options.primaryFundingSource());
            requireFundingAvailable(userId, plan, options.fallbackFundingSource());
        }
        if (mapper.updateRenewalSettings(subscriptionId, userId, expectedVersion,
            options.autoRenew(), options.primaryFundingSource(), options.fallbackFundingSource(),
            new Date()) != 1) {
            throw new IllegalStateException("Thuê bao đã được cập nhật đồng thời");
        }
        return mapper.selectCurrentSubscriptionByUserId(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReadingSubscriptionRow consentPrice(long userId, long subscriptionId,
                                               long expectedVersion,
                                               long acceptedPlanVersion,
                                               String clientRequestId) {
        String requestId = normalizeClientRequestId(clientRequestId);
        ReadingSubscriptionRow subscription = requireOwnedSubscription(
            userId, subscriptionId, expectedVersion);
        ReadingSubscriptionPlanRow plan = mapper.selectPlanById(subscription.getPlanId());
        if (plan == null || !Objects.equals(plan.getPlanVersion(), acceptedPlanVersion)) {
            throw new IllegalStateException("Phiên bản giá cần chấp nhận không còn hiện hành");
        }
        if (Objects.equals(subscription.getAcceptedPlanVersion(), acceptedPlanVersion)) {
            return subscription;
        }
        Date consentedAt = new Date();
        try {
            if (mapper.insertPriceConsent(subscription, plan, requestId, consentedAt) != 1) {
                throw new IllegalStateException("Không thể ghi bằng chứng chấp nhận giá");
            }
        } catch (DuplicateKeyException exception) {
            ReadingSubscriptionRow replay = mapper.selectCurrentSubscriptionByUserId(userId);
            if (replay != null
                && Objects.equals(replay.getAcceptedPlanVersion(), acceptedPlanVersion)) {
                return replay;
            }
            throw new IllegalStateException("Mã yêu cầu chấp nhận giá đã được sử dụng", exception);
        }
        if (mapper.applyPriceConsent(subscriptionId, userId, expectedVersion, plan, consentedAt) != 1) {
            throw new IllegalStateException("Thuê bao đã được cập nhật đồng thời");
        }
        return mapper.selectCurrentSubscriptionByUserId(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReadingSubscriptionRow cancelAtPeriodEnd(long userId, long subscriptionId,
                                                    long expectedVersion) {
        ReadingSubscriptionRow subscription = requireOwnedSubscription(
            userId, subscriptionId, expectedVersion);
        Date now = new Date();
        mandateMapper.requestRevocation(userId, now);
        if ("CANCEL_AT_PERIOD_END".equals(subscription.getStatus())) {
            return subscription;
        }
        if (mapper.cancelAtPeriodEnd(subscriptionId, userId, expectedVersion, now) != 1) {
            throw new IllegalStateException("Thuê bao đã được cập nhật đồng thời");
        }
        return mapper.selectCurrentSubscriptionByUserId(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReadingSubscriptionMandateQueueItem> listMandateQueue(String status, int limit) {
        String normalized = status == null ? "REVOKE_PENDING"
            : status.trim().toUpperCase(java.util.Locale.ROOT);
        if (!List.of("PENDING", "FAILED", "REVOKE_PENDING", "REVOKED").contains(normalized)
            || limit < 1 || limit > 500) {
            throw new IllegalArgumentException("Hàng đợi mandate hoặc giới hạn không hợp lệ");
        }
        return mandateMapper.selectMandateQueue(normalized, limit);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReadingSubscriptionMandateAdminAuditRow> listMandateAdminAudits(
        long mandateId, int limit) {
        if (mandateId <= 0 || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Yêu cầu xem audit mandate không hợp lệ");
        }
        return mandateMapper.selectMandateAdminAudits(mandateId, limit);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReadingSubscriptionMandateAdminResult adminScheduleMandateRevocationRetry(
        long mandateId, long expectedVersion, long operatorId, String reason, Date now) {
        String normalizedReason = normalizeReviewReason(operatorId, reason);
        if (mandateId <= 0 || expectedVersion < 0 || now == null) {
            throw new IllegalArgumentException("Mandate hoặc phiên bản không hợp lệ");
        }
        ReadingSubscriptionMandateRow mandate = mandateMapper.selectByIdForUpdate(mandateId);
        if (mandate == null) {
            throw new IllegalStateException("Mandate không tồn tại");
        }
        if (!Objects.equals(mandate.getVersion(), expectedVersion)) {
            throw new IllegalStateException("Mandate đã được cập nhật đồng thời");
        }
        if (!"REVOKE_PENDING".equals(mandate.getStatus())) {
            throw new IllegalStateException("Chỉ được retry mandate đang chờ thu hồi");
        }
        if (mandate.getRevokeLeaseUntil() != null && mandate.getRevokeLeaseUntil().after(now)) {
            throw new IllegalStateException("Mandate đang được worker xử lý");
        }
        if (mandateMapper.adminScheduleRevocationRetry(mandateId, expectedVersion, now) != 1
            || mandateMapper.insertMandateAdminAudit(mandateId, mandate.getUserId(), operatorId,
                "RETRY_SCHEDULED", mandate.getStatus(), mandate.getStatus(), normalizedReason,
                now) != 1) {
            throw new IllegalStateException("Mandate đã được cập nhật đồng thời");
        }
        return ReadingSubscriptionMandateAdminResult.RETRY_SCHEDULED;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReadingSubscriptionPeriodGrantRow> listPeriodGrants(
        long userId, long subscriptionId, int limit) {
        if (userId <= 0 || subscriptionId <= 0 || limit <= 0 || limit > 100) {
            throw new IllegalArgumentException("Yêu cầu xem lịch sử cấp Vé đọc không hợp lệ");
        }
        return mapper.selectPeriodGrantsByUser(userId, subscriptionId, limit);
    }

    @Override
    @Transactional(readOnly = true)
    public ReadingSubscriptionPurchasePage listPurchaseReviews(
        String status, int page, int pageSize) {
        String normalizedStatus = normalizeReviewStatus(status);
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("Phân trang đơn thuê bao cần xử lý không hợp lệ");
        }
        long offset;
        try {
            offset = Math.multiplyExact((long) page - 1L, pageSize);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Phân trang đơn thuê bao cần xử lý không hợp lệ",
                exception);
        }
        return new ReadingSubscriptionPurchasePage(
            purchaseMapper.selectReviews(normalizedStatus, offset, pageSize),
            purchaseMapper.countReviews(normalizedStatus), page, pageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReadingSubscriptionPurchaseReviewResult retryPurchaseActivation(
        long purchaseId, long expectedVersion, long operatorId, String reason) {
        String normalizedReason = normalizeReviewReason(operatorId, reason);
        ReadingSubscriptionPurchaseRow purchase = requirePaidReview(purchaseId, expectedVersion);
        if (purchase.getSettledAt() == null) {
            throw new IllegalStateException("Đơn thuê bao cần xử lý chưa có thời điểm thanh toán");
        }
        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(purchase.getZoneId());
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Múi giờ snapshot của đơn thuê bao không hợp lệ",
                exception);
        }
        ZonedDateTime start = purchase.getSettledAt().toInstant().atZone(zoneId);
        Date endAt = Date.from(start.plusMonths(purchase.getPeriodMonthsSnapshot()).toInstant());
        ReadingSubscriptionRow activated = activatePurchase(
            new ReadingSubscriptionPurchaseActivationCommand(
                purchase.getUserId(), purchase.getPlanId(), purchase.getPlanCodeSnapshot(),
                purchase.getTicketsPerPeriodSnapshot(), purchase.getPeriodMonthsSnapshot(),
                purchase.getTicketValidityDaysSnapshot(), purchase.getSettledAt(), endAt,
                Long.toString(purchase.getOutTradeNo()), purchase.getPolicyVersion(),
                purchase.getPlanVersionSnapshot(), purchase.getPriceVndSnapshot(),
                purchase.getPriceXuSnapshot(), Boolean.TRUE.equals(purchase.getAutoRenew()),
                purchase.getPrimaryFundingSource(), purchase.getFallbackFundingSource(),
                purchase.getAcceptedPlanVersion()));
        Date reviewedAt = new Date();
        if (activated == null) {
            insertReviewAudit(purchase, "RETRY_BLOCKED", "PAID_REVIEW", operatorId,
                normalizedReason, purchase.getVersion(), reviewedAt);
            return ReadingSubscriptionPurchaseReviewResult.BLOCKED_BY_OPEN_SUBSCRIPTION;
        }
        if (purchaseMapper.markReviewActivated(purchase.getId(), purchase.getVersion(),
            activated.getId(), reviewedAt) != 1) {
            throw new IllegalStateException("Đơn thuê bao đã được xử lý đồng thời");
        }
        insertReviewAudit(purchase, "RETRY_ACTIVATED", "ACTIVATED", operatorId,
            normalizedReason, purchase.getVersion() + 1L, reviewedAt);
        return ReadingSubscriptionPurchaseReviewResult.ACTIVATED;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReadingSubscriptionPurchaseReviewResult sendPurchaseToRefund(
        long purchaseId, long expectedVersion, long operatorId, String reason) {
        String normalizedReason = normalizeReviewReason(operatorId, reason);
        ReadingSubscriptionPurchaseRow purchase = requirePaidReview(purchaseId, expectedVersion);
        Date reviewedAt = new Date();
        if (purchaseMapper.markRefundPending(
            purchase.getId(), purchase.getVersion(), reviewedAt) != 1) {
            throw new IllegalStateException("Đơn thuê bao đã được xử lý đồng thời");
        }
        insertReviewAudit(purchase, "REFUND_REQUESTED", "REFUND_PENDING", operatorId,
            normalizedReason, purchase.getVersion() + 1L, reviewedAt);
        return ReadingSubscriptionPurchaseReviewResult.REFUND_PENDING;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReadingSubscriptionRow activate(ReadingSubscriptionActivationCommand command) {
        Objects.requireNonNull(command, "Thiếu yêu cầu kích hoạt thuê bao");
        ReadingSubscriptionRow existing = mapper.selectSubscriptionBySource(
            command.sourceType(), command.sourceRef());
        if (existing != null) {
            validateReplay(existing, command);
            return existing;
        }
        ReadingSubscriptionPlanRow plan = mapper.selectActivePlanByCode(command.planCode());
        if (plan == null) {
            throw new IllegalStateException("Gói thuê bao không tồn tại hoặc chưa hoạt động");
        }
        try {
            if (mapper.insertSubscription(plan, command) != 1) {
                throw new IllegalStateException("Không thể kích hoạt thuê bao");
            }
        } catch (DuplicateKeyException exception) {
            existing = mapper.selectSubscriptionBySourceForUpdate(
                command.sourceType(), command.sourceRef());
            if (existing == null) {
                throw exception;
            }
            validateReplay(existing, command);
            return existing;
        }
        ReadingSubscriptionRow created = mapper.selectSubscriptionBySource(
            command.sourceType(), command.sourceRef());
        if (created == null) {
            throw new IllegalStateException("Không đọc được thuê bao vừa kích hoạt");
        }
        validateReplay(created, command);
        return created;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReadingSubscriptionRow activatePurchase(
        ReadingSubscriptionPurchaseActivationCommand command) {
        Objects.requireNonNull(command, "Thiếu yêu cầu kích hoạt thuê bao đã mua");
        ReadingSubscriptionRow existing = mapper.selectSubscriptionBySource(
            "PAYMENT", command.sourceRef());
        if (existing != null) {
            validatePurchaseReplay(existing, command);
            return existing;
        }
        try {
            if (mapper.insertPurchasedSubscription(command) != 1) {
                return null;
            }
        } catch (DuplicateKeyException exception) {
            existing = mapper.selectSubscriptionBySourceForUpdate("PAYMENT", command.sourceRef());
            if (existing == null) {
                return null;
            }
            validatePurchaseReplay(existing, command);
            return existing;
        }
        ReadingSubscriptionRow created = mapper.selectSubscriptionBySource(
            "PAYMENT", command.sourceRef());
        if (created == null) {
            throw new IllegalStateException("Không đọc được thuê bao thanh toán vừa kích hoạt");
        }
        validatePurchaseReplay(created, command);
        return created;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReadingSubscriptionGrantResult grantDuePeriod(long subscriptionId, Date now,
                                                          ZoneId zoneId, String policyVersion) {
        if (subscriptionId <= 0 || now == null || zoneId == null || policyVersion == null
            || policyVersion.isBlank() || policyVersion.length() > 32) {
            throw new IllegalArgumentException("Yêu cầu cấp Vé đọc theo kỳ không hợp lệ");
        }
        ReadingSubscriptionRow row = mapper.lockDueSubscription(subscriptionId, now);
        if (row == null) {
            return ReadingSubscriptionGrantResult.notDue(subscriptionId);
        }
        Date periodStart = row.getNextGrantAt();
        ZonedDateTime start = periodStart.toInstant().atZone(zoneId);
        Date naturalEnd = Date.from(start.plusMonths(row.getPeriodMonthsSnapshot()).toInstant());
        Date periodEnd = row.getEndAt() != null && row.getEndAt().before(naturalEnd)
            ? row.getEndAt() : naturalEnd;
        Date ticketExpireAt = Date.from(start.plusDays(row.getTicketValidityDaysSnapshot()).toInstant());
        String periodToken = Long.toString(periodStart.getTime());
        String idempotencyKey = "SUBSCRIPTION:" + row.getId() + ':' + periodToken;
        ReadingTicketPostResult posted = ticketService.grant(new ReadingTicketGrantCommand(
            row.getUserId(), row.getTicketsPerPeriodSnapshot(), "SUBSCRIPTION",
            row.getId() + ":" + periodToken, idempotencyKey, periodStart, ticketExpireAt,
            "SYSTEM", null, "Cấp Vé đọc theo kỳ thuê bao", policyVersion));
        if (mapper.insertPeriodGrantFromLedger(row.getId(), row.getUserId(), periodStart,
            periodEnd, row.getTicketsPerPeriodSnapshot(), ticketExpireAt, idempotencyKey) != 1) {
            throw new IllegalStateException("Không thể ghi biên nhận cấp Vé đọc theo kỳ");
        }
        String nextStatus = row.getEndAt() != null && !periodEnd.before(row.getEndAt())
            ? "EXPIRED" : "ACTIVE";
        if (mapper.advanceSubscription(row.getId(), row.getVersion(), periodEnd, nextStatus) != 1) {
            throw new IllegalStateException("Thuê bao đã được cập nhật đồng thời");
        }
        ReadingSubscriptionGrantStatus status = posted == ReadingTicketPostResult.ALREADY_POSTED
            ? ReadingSubscriptionGrantStatus.ALREADY_POSTED : ReadingSubscriptionGrantStatus.POSTED;
        return new ReadingSubscriptionGrantResult(status, row.getId(), periodStart,
            periodEnd, row.getTicketsPerPeriodSnapshot());
    }

    private void validateReplay(ReadingSubscriptionRow row,
                                ReadingSubscriptionActivationCommand command) {
        if (!Objects.equals(row.getUserId(), command.userId())
            || !Objects.equals(row.getPlanCodeSnapshot(), command.planCode())
            || !Objects.equals(row.getStartAt(), command.startAt())
            || !Objects.equals(row.getEndAt(), command.endAt())
            || !Objects.equals(row.getSourceType(), command.sourceType())
            || !Objects.equals(row.getSourceRef(), command.sourceRef())
            || !Objects.equals(row.getPolicyVersion(), command.policyVersion())) {
            throw new IllegalStateException("Source kích hoạt thuê bao đã dùng cho nội dung khác");
        }
    }

    private void validatePurchaseReplay(ReadingSubscriptionRow row,
                                        ReadingSubscriptionPurchaseActivationCommand command) {
        if (!Objects.equals(row.getUserId(), command.userId())
            || !Objects.equals(row.getPlanId(), command.planId())
            || !Objects.equals(row.getPlanCodeSnapshot(), command.planCode())
            || !Objects.equals(row.getPlanVersionSnapshot(), command.planVersion())
            || !Objects.equals(row.getPriceVndSnapshot(), command.priceVnd())
            || !Objects.equals(row.getPriceXuSnapshot(), command.priceXu())
            || !Objects.equals(row.getAutoRenew(), command.autoRenew())
            || !Objects.equals(row.getPrimaryFundingSource(), command.primaryFundingSource())
            || !Objects.equals(row.getFallbackFundingSource(), command.fallbackFundingSource())
            || !Objects.equals(row.getAcceptedPlanVersion(), command.acceptedPlanVersion())
            || !Objects.equals(row.getTicketsPerPeriodSnapshot(), command.ticketsPerPeriod())
            || !Objects.equals(row.getPeriodMonthsSnapshot(), command.periodMonths())
            || !Objects.equals(row.getTicketValidityDaysSnapshot(), command.ticketValidityDays())
            || !Objects.equals(row.getStartAt(), command.startAt())
            || !Objects.equals(row.getEndAt(), command.endAt())
            || !"PAYMENT".equals(row.getSourceType())
            || !Objects.equals(row.getSourceRef(), command.sourceRef())
            || !Objects.equals(row.getPolicyVersion(), command.policyVersion())) {
            throw new IllegalStateException("Source thanh toán thuê bao đã dùng cho nội dung khác");
        }
    }

    private ReadingSubscriptionPlanRow requirePlanVersion(long planId, long expectedVersion) {
        if (planId <= 0 || expectedVersion < 0) {
            throw new IllegalArgumentException("Gói thuê bao hoặc phiên bản không hợp lệ");
        }
        ReadingSubscriptionPlanRow current = mapper.selectPlanById(planId);
        if (current == null) {
            throw new IllegalStateException("Gói thuê bao không tồn tại");
        }
        if (!Objects.equals(current.getVersion(), expectedVersion)) {
            throw new IllegalStateException("Gói thuê bao đã được cập nhật đồng thời");
        }
        return current;
    }

    private ReadingSubscriptionRow requireOwnedSubscription(
        long userId, long subscriptionId, long expectedVersion) {
        if (userId <= 0 || subscriptionId <= 0 || expectedVersion < 0) {
            throw new IllegalArgumentException("Thuê bao hoặc phiên bản không hợp lệ");
        }
        ReadingSubscriptionRow subscription = mapper.selectSubscriptionForUserForUpdate(
            subscriptionId, userId);
        if (subscription == null) {
            throw new IllegalStateException("Không tìm thấy thuê bao của người dùng");
        }
        if (!Objects.equals(subscription.getVersion(), expectedVersion)) {
            throw new IllegalStateException("Thuê bao đã được cập nhật đồng thời");
        }
        if (!List.of("ACTIVE", "PAST_DUE", "PENDING_PRICE_CONSENT",
            "CANCEL_AT_PERIOD_END").contains(subscription.getStatus())) {
            throw new IllegalStateException("Thuê bao không còn mở");
        }
        return subscription;
    }

    private void requireFundingAvailable(long userId, ReadingSubscriptionPlanRow plan,
                                         String fundingSource) {
        if (fundingSource == null) {
            return;
        }
        if ("WALLET_XU".equals(fundingSource)
            && (plan.getPriceXu() == null || plan.getPriceXu() <= 0)) {
            throw new IllegalStateException("Gói thuê bao chưa có giá Xu");
        }
        if ("VNPAY_RECURRING".equals(fundingSource)
            && mapper.countActiveMandates(userId) != 1) {
            throw new IllegalStateException("Chưa có ủy quyền VNPAY Recurring đang hoạt động");
        }
    }

    private String normalizeClientRequestId(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!normalized.matches("[A-Za-z0-9_-]{8,64}")) {
            throw new IllegalArgumentException("Mã yêu cầu không hợp lệ");
        }
        return normalized;
    }

    private ReadingSubscriptionPurchaseRow requirePaidReview(
        long purchaseId, long expectedVersion) {
        if (purchaseId <= 0 || expectedVersion < 0) {
            throw new IllegalArgumentException("Đơn thuê bao hoặc phiên bản không hợp lệ");
        }
        ReadingSubscriptionPurchaseRow purchase = purchaseMapper.selectByIdForUpdate(purchaseId);
        if (purchase == null) {
            throw new IllegalStateException("Đơn thuê bao không tồn tại");
        }
        if (!Objects.equals(purchase.getVersion(), expectedVersion)) {
            throw new IllegalStateException("Đơn thuê bao đã được xử lý đồng thời");
        }
        if (!"PAID_REVIEW".equals(purchase.getStatus())) {
            throw new IllegalStateException("Chỉ được xử lý đơn ở trạng thái PAID_REVIEW");
        }
        return purchase;
    }

    private void insertReviewAudit(ReadingSubscriptionPurchaseRow purchase, String eventType,
                                   String toStatus, long operatorId, String reason,
                                   long versionAfter, Date createdAt) {
        if (purchaseMapper.insertReviewAudit(purchase, eventType, toStatus, operatorId,
            reason, versionAfter, createdAt) != 1) {
            throw new IllegalStateException("Không thể ghi nhật ký xử lý đơn thuê bao");
        }
    }

    private String normalizeReviewStatus(String status) {
        String normalized = status == null ? "PAID_REVIEW"
            : status.trim().toUpperCase(java.util.Locale.ROOT);
        if (!"PAID_REVIEW".equals(normalized) && !"REFUND_PENDING".equals(normalized)) {
            throw new IllegalArgumentException("Trạng thái hàng đợi thuê bao không hợp lệ");
        }
        return normalized;
    }

    private String normalizeReviewReason(long operatorId, String reason) {
        String normalized = reason == null ? "" : reason.trim();
        if (operatorId <= 0 || normalized.length() < 8 || normalized.length() > 500) {
            throw new IllegalArgumentException("Lý do xử lý phải từ 8 đến 500 ký tự");
        }
        return normalized;
    }
}
