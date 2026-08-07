package com.java2nb.novel.service.impl;

import com.java2nb.novel.entity.OrderPay;
import com.java2nb.novel.mapper.OrderPayDynamicSqlSupport;
import com.java2nb.novel.mapper.OrderPayMapper;
import com.java2nb.novel.mapper.ReadingSubscriptionMapper;
import com.java2nb.novel.mapper.ReadingSubscriptionPurchaseMapper;
import com.java2nb.novel.core.config.ReaderEntitlementProperties;
import com.java2nb.novel.service.OrderService;
import com.java2nb.novel.service.PayOrderCreation;
import com.java2nb.novel.service.PayOrderSnapshot;
import com.java2nb.novel.service.PayOrderState;
import com.java2nb.novel.service.PayOrderUpdateResult;
import com.java2nb.novel.service.ReadingSubscriptionCheckoutCreation;
import com.java2nb.novel.service.wallet.WalletLedgerService;
import com.java2nb.novel.service.gamification.GamificationEventService;
import com.java2nb.novel.service.subscription.ReadingSubscriptionPlanRow;
import com.java2nb.novel.service.subscription.ReadingSubscriptionCheckoutOptions;
import com.java2nb.novel.service.subscription.ReadingSubscriptionPurchaseActivationCommand;
import com.java2nb.novel.service.subscription.ReadingSubscriptionPurchaseRow;
import com.java2nb.novel.service.subscription.ReadingSubscriptionRow;
import com.java2nb.novel.service.subscription.ReadingSubscriptionService;
import lombok.RequiredArgsConstructor;
import org.mybatis.dynamic.sql.render.RenderingStrategies;
import org.mybatis.dynamic.sql.select.render.SelectStatementProvider;
import org.mybatis.dynamic.sql.update.render.UpdateStatementProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;
import static org.mybatis.dynamic.sql.SqlBuilder.isGreaterThan;
import static org.mybatis.dynamic.sql.SqlBuilder.isGreaterThanOrEqualTo;
import static org.mybatis.dynamic.sql.SqlBuilder.isLessThanOrEqualTo;
import static org.mybatis.dynamic.sql.SqlBuilder.update;
import static org.mybatis.dynamic.sql.select.SelectDSL.select;

/**
 * @author 11797
 */
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final int ORDER_NUMBER_RETRY_LIMIT = 5;
    private static final SecureRandom ORDER_NUMBER_RANDOM = new SecureRandom();

    private final OrderPayMapper orderPayMapper;

    private final WalletLedgerService walletLedgerService;

    private final GamificationEventService gamificationEventService;

    private final ReadingSubscriptionMapper readingSubscriptionMapper;

    private final ReadingSubscriptionPurchaseMapper purchaseMapper;

    private final ReadingSubscriptionService readingSubscriptionService;

    private final ReaderEntitlementProperties entitlementProperties;


    @Override
    public PayOrderCreation createPayOrder(Byte payChannel, Integer payAmount, Integer accountAmount, Long userId) {
        Date currentDate = new Date();
        for (int attempt = 0; attempt < ORDER_NUMBER_RETRY_LIMIT; attempt++) {
            long outTradeNo = nextOrderNumber();
            OrderPay orderPay = new OrderPay();
            orderPay.setOutTradeNo(outTradeNo);
            orderPay.setPayChannel(payChannel);
            orderPay.setTotalAmount(payAmount);
            orderPay.setAccountAmount(accountAmount);
            orderPay.setUserId(userId);
            orderPay.setPayStatus((byte) 2);
            orderPay.setCreateTime(currentDate);
            orderPay.setUpdateTime(currentDate);
            try {
                if (orderPayMapper.insertSelective(orderPay) == 1) {
                    return new PayOrderCreation(outTradeNo, new Date(currentDate.getTime()));
                }
            } catch (DuplicateKeyException exception) {
                if (attempt == ORDER_NUMBER_RETRY_LIMIT - 1) {
                    throw exception;
                }
            }
        }
        throw new IllegalStateException("Không thể tạo mã đơn thanh toán duy nhất");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReadingSubscriptionCheckoutCreation createSubscriptionCheckout(
        byte payChannel, long userId, String planCode, String clientRequestId,
        ReadingSubscriptionCheckoutOptions options) {
        String normalizedPlanCode = planCode == null ? ""
            : planCode.trim().toUpperCase(Locale.ROOT);
        String normalizedRequestId = clientRequestId == null ? "" : clientRequestId.trim();
        if ((payChannel != 4 && payChannel != 5) || userId <= 0
            || !normalizedPlanCode.matches("[A-Z0-9_]{3,32}")
            || !normalizedRequestId.matches("[A-Za-z0-9_-]{8,64}")
            || options == null || !entitlementProperties.isConfigured()) {
            throw new IllegalArgumentException("Yêu cầu mua thuê bao không hợp lệ");
        }
        ReadingSubscriptionPlanRow plan = readingSubscriptionMapper
            .selectActivePlanByCode(normalizedPlanCode);
        if (plan == null || plan.getPriceVnd() == null || plan.getPriceVnd() < 1_000
            || plan.getPriceVnd() > 100_000_000) {
            throw new IllegalStateException("Gói thuê bao không mở bán hoặc chưa có giá hợp lệ");
        }
        if (!Objects.equals(plan.getPlanVersion(), options.acceptedPlanVersion())
            || (options.autoRenew() && "WALLET_XU".equals(options.primaryFundingSource())
                && plan.getPriceXu() == null)
            || (options.autoRenew() && "WALLET_XU".equals(options.fallbackFundingSource())
                && plan.getPriceXu() == null)) {
            throw new IllegalStateException("Phiên bản hoặc giá tự gia hạn của gói không hợp lệ");
        }
        if (options.autoRenew()
            && ("VNPAY_RECURRING".equals(options.primaryFundingSource())
                || "VNPAY_RECURRING".equals(options.fallbackFundingSource()))
            && readingSubscriptionMapper.countActiveMandates(userId) != 1) {
            throw new IllegalStateException("Chưa có ủy quyền VNPAY Recurring đang hoạt động");
        }
        String requestHash = checkoutRequestHash(userId, payChannel, normalizedRequestId, plan, options);
        ReadingSubscriptionPurchaseRow existing = purchaseMapper.selectByUserRequest(
            userId, normalizedRequestId);
        if (existing != null) {
            validateCheckoutReplay(existing, payChannel, plan, options, requestHash);
            return checkoutResult(existing, true);
        }
        if (readingSubscriptionMapper.selectCurrentSubscriptionByUserId(userId) != null) {
            throw new IllegalStateException("Tài khoản đang có thuê bao mở");
        }
        ReadingSubscriptionPurchaseRow open = purchaseMapper.selectOpenByUser(userId);
        if (open != null) {
            throw new IllegalStateException("Tài khoản đang có đơn mua thuê bao chưa kết thúc");
        }

        Date createdAt = new Date();
        for (int attempt = 0; attempt < ORDER_NUMBER_RETRY_LIMIT; attempt++) {
            long outTradeNo = nextOrderNumber();
            try {
                if (purchaseMapper.insertPurchase(outTradeNo, userId, plan, options, payChannel,
                    normalizedRequestId, requestHash, entitlementProperties.getPolicyVersion(),
                    entitlementProperties.getSubscriptionZoneId(), createdAt) != 1) {
                    throw new IllegalStateException("Không thể tạo đơn mua thuê bao");
                }
            } catch (DuplicateKeyException exception) {
                existing = purchaseMapper.selectByUserRequest(userId, normalizedRequestId);
                if (existing != null) {
                    validateCheckoutReplay(existing, payChannel, plan, options, requestHash);
                    return checkoutResult(existing, true);
                }
                open = purchaseMapper.selectOpenByUser(userId);
                if (open != null) {
                    throw new IllegalStateException(
                        "Tài khoản đang có đơn mua thuê bao chưa kết thúc", exception);
                }
                if (attempt < ORDER_NUMBER_RETRY_LIMIT - 1) {
                    continue;
                }
                throw exception;
            }
            OrderPay order = pendingOrder(outTradeNo, payChannel,
                Math.toIntExact(plan.getPriceVnd()), 0, userId, createdAt);
            if (orderPayMapper.insertSelective(order) != 1) {
                throw new IllegalStateException("Không thể tạo đơn thanh toán thuê bao");
            }
            return new ReadingSubscriptionCheckoutCreation(outTradeNo,
                Math.toIntExact(plan.getPriceVnd()), createdAt, false);
        }
        throw new IllegalStateException("Không thể tạo mã đơn mua thuê bao duy nhất");
    }

    @Override
    public PayOrderState inspectPayOrder(Long outTradeNo, byte payChannel, int totalAmount) {
        OrderPay orderPay = selectPayOrder(outTradeNo);
        if (orderPay == null) {
            return PayOrderState.NOT_FOUND;
        }
        if (orderPay.getPayChannel() == null || orderPay.getPayChannel() != payChannel) {
            return PayOrderState.INVALID_CHANNEL;
        }
        if (totalAmount > 0 && (orderPay.getTotalAmount() == null || orderPay.getTotalAmount() != totalAmount)) {
            return PayOrderState.INVALID_AMOUNT;
        }
        if (orderPay.getPayStatus() == null || orderPay.getPayStatus() == 2) {
            return PayOrderState.PENDING;
        }
        return orderPay.getPayStatus() == 1 ? PayOrderState.SUCCESS : PayOrderState.FAILED;
    }

    @Override
    public List<PayOrderSnapshot> listPendingPayOrders(byte payChannel, Date createdAfter, Date createdBefore,
                                                       Date updatedBefore, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        SelectStatementProvider selectStatement = select(OrderPayDynamicSqlSupport.id,
            OrderPayDynamicSqlSupport.outTradeNo, OrderPayDynamicSqlSupport.totalAmount,
            OrderPayDynamicSqlSupport.accountAmount, OrderPayDynamicSqlSupport.createTime,
            OrderPayDynamicSqlSupport.updateTime)
            .from(OrderPayDynamicSqlSupport.orderPay)
            .where(OrderPayDynamicSqlSupport.payChannel, isEqualTo(payChannel))
            .and(OrderPayDynamicSqlSupport.payStatus, isEqualTo((byte) 2))
            .and(OrderPayDynamicSqlSupport.accountAmount, isGreaterThanOrEqualTo(0))
            .and(OrderPayDynamicSqlSupport.createTime, isGreaterThanOrEqualTo(createdAfter))
            .and(OrderPayDynamicSqlSupport.createTime, isLessThanOrEqualTo(createdBefore))
            .and(OrderPayDynamicSqlSupport.updateTime, isLessThanOrEqualTo(updatedBefore))
            .orderBy(OrderPayDynamicSqlSupport.updateTime)
            .limit(safeLimit)
            .build()
            .render(RenderingStrategies.MYBATIS3);
        return orderPayMapper.selectMany(selectStatement).stream()
            .map(order -> new PayOrderSnapshot(order.getId(), order.getOutTradeNo(), order.getTotalAmount(),
                order.getAccountAmount(), order.getCreateTime(), order.getUpdateTime()))
            .toList();
    }

    @Override
    public boolean claimPendingPayOrder(long id, Date expectedUpdateTime, Date claimedAt) {
        UpdateStatementProvider updateStatement = update(OrderPayDynamicSqlSupport.orderPay)
            .set(OrderPayDynamicSqlSupport.updateTime).equalTo(claimedAt)
            .where(OrderPayDynamicSqlSupport.id, isEqualTo(id))
            .and(OrderPayDynamicSqlSupport.payStatus, isEqualTo((byte) 2))
            .and(OrderPayDynamicSqlSupport.updateTime, isEqualTo(expectedUpdateTime))
            .build()
            .render(RenderingStrategies.MYBATIS3);
        return orderPayMapper.update(updateStatement) == 1;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public PayOrderUpdateResult processPayOrder(Long outTradeNo, String tradeNo, byte payChannel, int totalAmount,
                                                boolean successful) {
        OrderPay orderPay = selectPayOrder(outTradeNo);
        if (orderPay == null) {
            return PayOrderUpdateResult.NOT_FOUND;
        }
        if (orderPay.getPayChannel() == null || orderPay.getPayChannel() != payChannel) {
            return PayOrderUpdateResult.INVALID_CHANNEL;
        }
        if (orderPay.getTotalAmount() == null || orderPay.getTotalAmount() != totalAmount) {
            return PayOrderUpdateResult.INVALID_AMOUNT;
        }
        ReadingSubscriptionPurchaseRow purchase = purchaseMapper
            .selectByOutTradeNoForUpdate(outTradeNo);
        if (purchase != null && !validPurchaseOrder(purchase, orderPay)) {
            return PayOrderUpdateResult.INVALID_ACCOUNT_AMOUNT;
        }
        if (purchase == null && successful
            && (orderPay.getAccountAmount() == null || orderPay.getAccountAmount() <= 0)) {
            return PayOrderUpdateResult.INVALID_ACCOUNT_AMOUNT;
        }
        if (orderPay.getPayStatus() == null || orderPay.getPayStatus() != 2) {
            return PayOrderUpdateResult.ALREADY_PROCESSED;
        }
        if (purchase != null && !"PENDING".equals(purchase.getStatus())) {
            return PayOrderUpdateResult.ALREADY_PROCESSED;
        }

        Date settledAt = new Date();
        UpdateStatementProvider updateStatement = update(OrderPayDynamicSqlSupport.orderPay)
            .set(OrderPayDynamicSqlSupport.tradeNo).equalTo(tradeNo)
            .set(OrderPayDynamicSqlSupport.payStatus).equalTo(successful ? (byte) 1 : (byte) 0)
            .set(OrderPayDynamicSqlSupport.updateTime).equalTo(settledAt)
            .where(OrderPayDynamicSqlSupport.id, isEqualTo(orderPay.getId()))
            .and(OrderPayDynamicSqlSupport.payStatus, isEqualTo((byte) 2))
            .build()
            .render(RenderingStrategies.MYBATIS3);
        if (orderPayMapper.update(updateStatement) == 0) {
            return PayOrderUpdateResult.ALREADY_PROCESSED;
        }
        if (purchase != null) {
            settleSubscriptionPurchase(purchase, settledAt, successful);
        } else if (successful) {
            walletLedgerService.creditReaderTopUp(orderPay.getUserId(), orderPay.getAccountAmount(),
                String.valueOf(outTradeNo), "VNPAY_TOP_UP:" + outTradeNo);
            gamificationEventService.ingest("TOP_UP_SETTLED", "TOPUP:" + outTradeNo,
                orderPay.getUserId(), null, settledAt, null);
        }
        return PayOrderUpdateResult.SUCCESS;
    }

    private OrderPay selectPayOrder(Long outTradeNo) {
        SelectStatementProvider selectStatement = select(OrderPayDynamicSqlSupport.id,
            OrderPayDynamicSqlSupport.payStatus, OrderPayDynamicSqlSupport.totalAmount,
            OrderPayDynamicSqlSupport.accountAmount, OrderPayDynamicSqlSupport.userId,
            OrderPayDynamicSqlSupport.payChannel)
            .from(OrderPayDynamicSqlSupport.orderPay)
            .where(OrderPayDynamicSqlSupport.outTradeNo, isEqualTo(outTradeNo))
            .build()
            .render(RenderingStrategies.MYBATIS3);
        return orderPayMapper.selectOne(selectStatement).orElse(null);
    }

    private void settleSubscriptionPurchase(ReadingSubscriptionPurchaseRow purchase,
                                            Date settledAt, boolean successful) {
        if (!successful) {
            if (purchaseMapper.markFailed(purchase.getId(), purchase.getVersion(), settledAt) != 1) {
                throw new IllegalStateException("Đơn mua thuê bao đã được xử lý đồng thời");
            }
            return;
        }
        ZonedDateTime start = settledAt.toInstant().atZone(ZoneId.of(purchase.getZoneId()));
        Date endAt = Date.from(start.plusMonths(purchase.getPeriodMonthsSnapshot()).toInstant());
        ReadingSubscriptionRow activated = readingSubscriptionService.activatePurchase(
            new ReadingSubscriptionPurchaseActivationCommand(
                purchase.getUserId(), purchase.getPlanId(), purchase.getPlanCodeSnapshot(),
                purchase.getTicketsPerPeriodSnapshot(), purchase.getPeriodMonthsSnapshot(),
                purchase.getTicketValidityDaysSnapshot(), settledAt, endAt,
                Long.toString(purchase.getOutTradeNo()), purchase.getPolicyVersion(),
                purchase.getPlanVersionSnapshot(), purchase.getPriceVndSnapshot(),
                purchase.getPriceXuSnapshot(), Boolean.TRUE.equals(purchase.getAutoRenew()),
                purchase.getPrimaryFundingSource(), purchase.getFallbackFundingSource(),
                purchase.getAcceptedPlanVersion()));
        int updated = activated == null
            ? purchaseMapper.markPaidReview(purchase.getId(), purchase.getVersion(), settledAt)
            : purchaseMapper.markActivated(purchase.getId(), purchase.getVersion(),
                activated.getId(), settledAt);
        if (updated != 1) {
            throw new IllegalStateException("Không thể chốt trạng thái đơn mua thuê bao");
        }
    }

    private boolean validPurchaseOrder(ReadingSubscriptionPurchaseRow purchase, OrderPay order) {
        return Objects.equals(purchase.getUserId(), order.getUserId())
            && Objects.equals(purchase.getPayChannel(), order.getPayChannel())
            && Objects.equals(purchase.getPriceVndSnapshot().intValue(), order.getTotalAmount())
            && Objects.equals(order.getAccountAmount(), 0);
    }

    private OrderPay pendingOrder(long outTradeNo, byte payChannel, int totalAmount,
                                  int accountAmount, long userId, Date createdAt) {
        OrderPay order = new OrderPay();
        order.setOutTradeNo(outTradeNo);
        order.setPayChannel(payChannel);
        order.setTotalAmount(totalAmount);
        order.setAccountAmount(accountAmount);
        order.setUserId(userId);
        order.setPayStatus((byte) 2);
        order.setCreateTime(createdAt);
        order.setUpdateTime(createdAt);
        return order;
    }

    private ReadingSubscriptionCheckoutCreation checkoutResult(
        ReadingSubscriptionPurchaseRow purchase, boolean replay) {
        return new ReadingSubscriptionCheckoutCreation(purchase.getOutTradeNo(),
            Math.toIntExact(purchase.getPriceVndSnapshot()), purchase.getCreateTime(), replay);
    }

    private void validateCheckoutReplay(ReadingSubscriptionPurchaseRow purchase, byte payChannel,
                                        ReadingSubscriptionPlanRow plan,
                                        ReadingSubscriptionCheckoutOptions options,
                                        String requestHash) {
        if (!Objects.equals(purchase.getPlanId(), plan.getId())
            || !Objects.equals(purchase.getPayChannel(), payChannel)
            || !Objects.equals(purchase.getPlanVersionSnapshot(), plan.getPlanVersion())
            || !Objects.equals(purchase.getPriceVndSnapshot(), plan.getPriceVnd())
            || !Objects.equals(purchase.getPriceXuSnapshot(), plan.getPriceXu())
            || !Objects.equals(purchase.getAutoRenew(), options.autoRenew())
            || !Objects.equals(purchase.getPrimaryFundingSource(), options.primaryFundingSource())
            || !Objects.equals(purchase.getFallbackFundingSource(), options.fallbackFundingSource())
            || !Objects.equals(purchase.getAcceptedPlanVersion(), options.acceptedPlanVersion())
            || !Objects.equals(purchase.getRequestHash(), requestHash)) {
            throw new IllegalStateException("Mã yêu cầu mua thuê bao đã dùng cho nội dung khác");
        }
    }

    private String checkoutRequestHash(long userId, byte payChannel, String clientRequestId,
                                       ReadingSubscriptionPlanRow plan,
                                       ReadingSubscriptionCheckoutOptions options) {
        return sha256("SUBSCRIPTION_CHECKOUT|" + userId + '|' + payChannel + '|'
            + clientRequestId + '|' + plan.getId() + '|' + plan.getPlanCode() + '|'
            + plan.getPlanName() + '|' + plan.getPlanVersion() + '|' + plan.getPriceVnd()
            + '|' + plan.getPriceXu() + '|' + options.autoRenew() + '|'
            + options.primaryFundingSource() + '|' + options.fallbackFundingSource() + '|'
            + options.acceptedPlanVersion() + '|'
            + plan.getTicketsPerPeriod() + '|' + plan.getPeriodMonths() + '|'
            + plan.getTicketValidityDays() + '|' + entitlementProperties.getPolicyVersion()
            + '|' + entitlementProperties.getSubscriptionZoneId());
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM không hỗ trợ SHA-256", exception);
        }
    }

    private long nextOrderNumber() {
        long candidate;
        do {
            candidate = ORDER_NUMBER_RANDOM.nextLong() & Long.MAX_VALUE;
        } while (candidate == 0);
        return candidate;
    }
}
