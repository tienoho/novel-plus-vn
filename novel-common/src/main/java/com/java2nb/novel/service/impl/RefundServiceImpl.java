package com.java2nb.novel.service.impl;

import com.java2nb.novel.entity.OrderPay;
import com.java2nb.novel.entity.OrderRefund;
import com.java2nb.novel.mapper.OrderPayDynamicSqlSupport;
import com.java2nb.novel.mapper.OrderPayMapper;
import com.java2nb.novel.mapper.OrderRefundMapper;
import com.java2nb.novel.mapper.WalletLedgerMapper;
import com.java2nb.novel.service.RefundService;
import com.java2nb.novel.service.wallet.LedgerTransactionRow;
import com.java2nb.novel.service.wallet.WalletLedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.dynamic.sql.render.RenderingStrategies;
import org.mybatis.dynamic.sql.select.render.SelectStatementProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;
import static org.mybatis.dynamic.sql.select.SelectDSL.select;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefundServiceImpl implements RefundService {

    private final OrderRefundMapper orderRefundMapper;
    private final OrderPayMapper orderPayMapper;
    private final WalletLedgerMapper walletLedgerMapper;
    private final WalletLedgerService walletLedgerService;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public OrderRefund requestRefund(long userId, long outTradeNo, String reason) {
        OrderPay orderPay = findOrderPay(outTradeNo);
        if (orderPay == null) {
            throw new IllegalArgumentException("Không tìm thấy đơn nạp tiền mã: " + outTradeNo);
        }
        if (!Long.valueOf(userId).equals(orderPay.getUserId())) {
            throw new IllegalArgumentException("Đơn nạp không thuộc về tài khoản này");
        }
        if (orderPay.getPayStatus() == null || orderPay.getPayStatus() != 1) {
            throw new IllegalStateException("Chỉ được yêu cầu hoàn tiền cho đơn đã nạp thành công");
        }

        String idempotencyKey = "REFUND_REQ:" + outTradeNo;
        OrderRefund existing = orderRefundMapper.selectByIdempotencyKey(idempotencyKey);
        if (existing != null) {
            return existing;
        }
        existing = orderRefundMapper.selectByOutTradeNo(outTradeNo);
        if (existing != null) {
            throw new IllegalStateException("Đơn hàng này đã có yêu cầu hoàn tiền/chargeback đang xử lý");
        }

        LedgerTransactionRow origTx = findOriginalTopUpTransaction(outTradeNo);
        if (origTx == null || origTx.getIdempotencyKey() == null) {
            throw new IllegalStateException("Không tìm thấy bút toán sổ cái gốc cho đơn nạp " + outTradeNo);
        }
        long refundXu = orderPay.getAccountAmount() != null ? orderPay.getAccountAmount().longValue() : 0L;
        if (orderPay.getTotalAmount() == null || orderPay.getTotalAmount() <= 0 || refundXu <= 0) {
            throw new IllegalStateException("Đơn nạp không có số tiền hoặc số Xu hợp lệ để hoàn");
        }

        OrderRefund refund = OrderRefund.builder()
            .refundNo("RF-" + outTradeNo)
            .outTradeNo(outTradeNo)
            .userId(userId)
            .refundAmountVnd(orderPay.getTotalAmount())
            .refundXu(refundXu)
            .type("REFUND")
            .status("REQUESTED")
            .reason(reason != null ? reason.trim() : "Độc giả yêu cầu hoàn tiền")
            .originalLedgerTransactionId(origTx != null ? origTx.getId() : null)
            .idempotencyKey(idempotencyKey)
            .version(0L)
            .build();

        if (orderRefundMapper.insert(refund) != 1) {
            throw new IllegalStateException("Không thể tạo yêu cầu hoàn tiền");
        }
        audit(refund, "REQUEST_CREATED", null, "REQUESTED", "USER", userId, refund.getReason());
        log.info("Tạo thành công yêu cầu hoàn tiền refundNo: {}, outTradeNo: {}", refund.getRefundNo(), outTradeNo);
        return refund;
    }

    @Override
    public List<OrderRefund> listRefunds(Map<String, Object> params) {
        return orderRefundMapper.list(params);
    }

    @Override
    public int countRefunds(Map<String, Object> params) {
        return orderRefundMapper.count(params);
    }

    @Override
    public OrderRefund getRefundById(long id) {
        OrderRefund refund = orderRefundMapper.selectById(id);
        if (refund == null) {
            throw new IllegalArgumentException("Không tìm thấy yêu cầu hoàn tiền id: " + id);
        }
        return refund;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public OrderRefund approveRefund(long refundId, long actorId) {
        OrderRefund refund = getRefundById(refundId);
        if ("APPROVED".equals(refund.getStatus()) || "REVERSED".equals(refund.getStatus())) {
            return refund;
        }
        if (!"REQUESTED".equals(refund.getStatus())) {
            throw new IllegalStateException("Yêu cầu hoàn tiền không còn ở trạng thái chờ duyệt");
        }

        String holdKey = "REFUND_HOLD:" + refund.getRefundNo();
        walletLedgerService.holdReaderRefund(refund.getUserId(), refund.getRefundXu(), refund.getRefundNo(), holdKey);
        LedgerTransactionRow holdTransaction = requireLedgerTransaction(holdKey);
        transition(refund, "REQUESTED", "APPROVED", holdTransaction.getId(), null, null,
            actorId, null, "REFUND_APPROVED", "ADMIN");
        log.info("Đã duyệt và giữ Xu cho yêu cầu hoàn tiền {}", refund.getRefundNo());
        return getRefundById(refundId);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public OrderRefund confirmRefund(long refundId, String providerReference, long actorId) {
        OrderRefund refund = getRefundById(refundId);
        String reference = requireProviderReference(providerReference);
        if ("REVERSED".equals(refund.getStatus())) {
            if (!reference.equals(refund.getProviderReference())) {
                throw new IllegalStateException("Yêu cầu hoàn tiền đã dùng mã provider khác");
            }
            return refund;
        }
        if (!"APPROVED".equals(refund.getStatus())) {
            throw new IllegalStateException("Yêu cầu hoàn tiền chưa ở trạng thái chờ provider xác nhận");
        }
        String settlementKey = "REFUND_SETTLED:" + refund.getRefundNo();
        walletLedgerService.settleReaderRefund(refund.getRefundXu(), refund.getRefundNo(), settlementKey);
        LedgerTransactionRow settlement = requireLedgerTransaction(settlementKey);
        transition(refund, "APPROVED", "REVERSED", null, settlement.getId(), reference,
            actorId, null, "PROVIDER_CONFIRMED", "ADMIN");
        return getRefundById(refundId);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public OrderRefund failRefund(long refundId, String reason, long actorId) {
        OrderRefund refund = getRefundById(refundId);
        if ("FAILED".equals(refund.getStatus())) {
            return refund;
        }
        if (!"APPROVED".equals(refund.getStatus())) {
            throw new IllegalStateException("Chỉ được ghi nhận provider thất bại sau khi đã giữ Xu");
        }
        String normalizedReason = requireReason(reason);
        String releaseKey = "REFUND_RELEASE:" + refund.getRefundNo();
        walletLedgerService.releaseReaderRefund(refund.getUserId(), refund.getRefundXu(), refund.getRefundNo(),
            releaseKey);
        LedgerTransactionRow release = requireLedgerTransaction(releaseKey);
        transition(refund, "APPROVED", "FAILED", null, release.getId(), null,
            actorId, normalizedReason, "PROVIDER_FAILED", "ADMIN");
        return getRefundById(refundId);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public OrderRefund rejectRefund(long refundId, String reason, long actorId) {
        OrderRefund refund = getRefundById(refundId);
        if (!"REQUESTED".equals(refund.getStatus())) {
            throw new IllegalStateException("Yêu cầu hoàn tiền không còn ở trạng thái chờ duyệt");
        }

        String normalizedReason = requireReason(reason);
        transition(refund, "REQUESTED", "REJECTED", null, null, null,
            actorId, normalizedReason, "REQUEST_REJECTED", "ADMIN");
        log.info("Từ chối hoàn tiền refundNo: {}", refund.getRefundNo());
        return getRefundById(refundId);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public OrderRefund recordChargeback(long outTradeNo, int refundAmountVnd, String reason, long actorId) {
        OrderPay orderPay = findOrderPay(outTradeNo);
        if (orderPay == null) {
            throw new IllegalArgumentException("Không tìm thấy đơn nạp tiền mã: " + outTradeNo);
        }
        if (orderPay.getPayStatus() == null || orderPay.getPayStatus() != 1) {
            throw new IllegalStateException("Chỉ được ghi nhận chargeback cho đơn đã thanh toán thành công");
        }
        if (orderPay.getTotalAmount() == null || refundAmountVnd != orderPay.getTotalAmount()) {
            throw new IllegalArgumentException("Hiện chỉ hỗ trợ chargeback toàn phần đúng bằng giá trị đơn gốc");
        }

        String idempotencyKey = "CHARGEBACK_REQ:" + outTradeNo;
        OrderRefund existing = orderRefundMapper.selectByIdempotencyKey(idempotencyKey);
        if (existing != null) {
            if (!Integer.valueOf(refundAmountVnd).equals(existing.getRefundAmountVnd())) {
                throw new IllegalStateException("Khóa chargeback đã được dùng với số tiền khác");
            }
            return existing;
        }
        if (orderRefundMapper.selectByOutTradeNo(outTradeNo) != null) {
            throw new IllegalStateException("Đơn đã có refund hoặc chargeback; cần đối soát thủ công để tránh hoàn trùng");
        }

        LedgerTransactionRow origTx = findOriginalTopUpTransaction(outTradeNo);
        if (origTx == null) {
            throw new IllegalStateException("Không tìm thấy bút toán sổ cái gốc cho đơn nạp " + outTradeNo);
        }

        if (origTx.getIdempotencyKey() == null) {
            throw new IllegalStateException("Bút toán gốc thiếu khóa idempotency");
        }
        String refundNo = "CB-" + outTradeNo;
        String reversalIdempotencyKey = "CHARGEBACK_REVERSAL:" + outTradeNo;
        walletLedgerService.chargebackReaderTopUp(orderPay.getUserId(), origTx.getIdempotencyKey(), refundNo,
            reversalIdempotencyKey, "Chargeback ngân hàng cho đơn " + outTradeNo + ": " + requireReason(reason));
        LedgerTransactionRow reversal = requireLedgerTransaction(reversalIdempotencyKey);

        OrderRefund refund = OrderRefund.builder()
            .refundNo(refundNo)
            .outTradeNo(outTradeNo)
            .userId(orderPay.getUserId())
            .refundAmountVnd(refundAmountVnd)
            .refundXu(orderPay.getAccountAmount() != null ? orderPay.getAccountAmount().longValue() : 0L)
            .type("CHARGEBACK")
            .status("REVERSED")
            .reason(requireReason(reason))
            .originalLedgerTransactionId(origTx.getId())
            .reversalLedgerTransactionId(reversal.getId())
            .idempotencyKey(idempotencyKey)
            .processedBy(actorId)
            .processedAt(new Date())
            .version(0L)
            .build();

        if (orderRefundMapper.insert(refund) != 1) {
            throw new IllegalStateException("Không thể ghi nhận chargeback");
        }
        audit(refund, "CHARGEBACK_RECORDED", null, "REVERSED", "ADMIN", actorId, refund.getReason());
        log.info("Ghi nhận Chargeback thành công refundNo: {}, outTradeNo: {}", refundNo, outTradeNo);
        return refund;
    }

    private LedgerTransactionRow findOriginalTopUpTransaction(long outTradeNo) {
        String[] keys = new String[] {
            "PAY_TOP_UP:" + outTradeNo,
            "VNPAY_TOP_UP:" + outTradeNo,
            "TOP_UP:" + outTradeNo,
            "VIETQR_TOP_UP:" + outTradeNo
        };
        for (String key : keys) {
            LedgerTransactionRow row = walletLedgerMapper.selectTransactionByIdempotencyKey(key);
            if (row != null) {
                return row;
            }
        }
        return null;
    }

    private LedgerTransactionRow requireLedgerTransaction(String idempotencyKey) {
        LedgerTransactionRow transaction = walletLedgerMapper.selectTransactionByIdempotencyKey(idempotencyKey);
        if (transaction == null || transaction.getId() == null) {
            throw new IllegalStateException("Không đọc được bút toán sổ cái theo khóa " + idempotencyKey);
        }
        return transaction;
    }

    private void transition(OrderRefund refund, String fromStatus, String toStatus,
                            Long holdLedgerTransactionId, Long reversalLedgerTransactionId,
                            String providerReference, long actorId, String reason,
                            String eventType, String actorType) {
        if (orderRefundMapper.transitionStatus(refund.getId(), fromStatus, toStatus, holdLedgerTransactionId,
            reversalLedgerTransactionId, providerReference, actorId, reason) != 1) {
            throw new IllegalStateException("Yêu cầu hoàn tiền đã được xử lý đồng thời");
        }
        audit(refund, eventType, fromStatus, toStatus, actorType, actorId, reason);
    }

    private void audit(OrderRefund refund, String eventType, String fromStatus, String toStatus,
                       String actorType, Long actorId, String reason) {
        if (orderRefundMapper.insertAudit(refund.getId(), refund.getRefundNo(), eventType, fromStatus, toStatus,
            actorType, actorId, reason) != 1) {
            throw new IllegalStateException("Không thể ghi audit yêu cầu hoàn tiền");
        }
    }

    private String requireReason(String reason) {
        String normalized = reason == null ? "" : reason.trim();
        if (normalized.length() < 3 || normalized.length() > 500) {
            throw new IllegalArgumentException("Lý do phải dài từ 3 đến 500 ký tự");
        }
        return normalized;
    }

    private String requireProviderReference(String providerReference) {
        String normalized = providerReference == null ? "" : providerReference.trim();
        if (normalized.length() < 3 || normalized.length() > 128) {
            throw new IllegalArgumentException("Mã xác nhận provider phải dài từ 3 đến 128 ký tự");
        }
        return normalized;
    }

    private OrderPay findOrderPay(long outTradeNo) {
        SelectStatementProvider selectStatement = select(
            OrderPayDynamicSqlSupport.id,
            OrderPayDynamicSqlSupport.outTradeNo,
            OrderPayDynamicSqlSupport.payStatus,
            OrderPayDynamicSqlSupport.totalAmount,
            OrderPayDynamicSqlSupport.accountAmount,
            OrderPayDynamicSqlSupport.userId,
            OrderPayDynamicSqlSupport.payChannel
            , OrderPayDynamicSqlSupport.tradeNo
            , OrderPayDynamicSqlSupport.createTime
        )
        .from(OrderPayDynamicSqlSupport.orderPay)
        .where(OrderPayDynamicSqlSupport.outTradeNo, isEqualTo(outTradeNo))
        .build()
        .render(RenderingStrategies.MYBATIS3);
        return orderPayMapper.selectOne(selectStatement).orElse(null);
    }
}
