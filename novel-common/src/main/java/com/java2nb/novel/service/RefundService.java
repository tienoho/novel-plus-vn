package com.java2nb.novel.service;

import com.java2nb.novel.entity.OrderRefund;

import java.util.List;
import java.util.Map;

public interface RefundService {

    OrderRefund requestRefund(long userId, long outTradeNo, String reason);

    List<OrderRefund> listRefunds(Map<String, Object> params);

    int countRefunds(Map<String, Object> params);

    OrderRefund getRefundById(long id);

    OrderRefund approveRefund(long refundId, long actorId);

    OrderRefund confirmRefund(long refundId, String providerReference, long actorId);

    OrderRefund failRefund(long refundId, String reason, long actorId);

    OrderRefund rejectRefund(long refundId, String reason, long actorId);

    OrderRefund recordChargeback(long outTradeNo, int refundAmountVnd, String reason, long actorId);
}
