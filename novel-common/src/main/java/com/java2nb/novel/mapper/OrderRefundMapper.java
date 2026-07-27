package com.java2nb.novel.mapper;

import com.java2nb.novel.entity.OrderRefund;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface OrderRefundMapper {

    int insert(OrderRefund refund);

    OrderRefund selectById(@Param("id") Long id);

    OrderRefund selectByRefundNo(@Param("refundNo") String refundNo);

    OrderRefund selectByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    OrderRefund selectByOutTradeNo(@Param("outTradeNo") Long outTradeNo);

    List<OrderRefund> list(Map<String, Object> params);

    int count(Map<String, Object> params);

    int transitionStatus(@Param("id") Long id,
                         @Param("expectedStatus") String expectedStatus,
                         @Param("nextStatus") String nextStatus,
                         @Param("holdLedgerTransactionId") Long holdLedgerTransactionId,
                         @Param("reversalLedgerTransactionId") Long reversalLedgerTransactionId,
                         @Param("providerReference") String providerReference,
                         @Param("processedBy") Long processedBy,
                         @Param("reason") String reason);

    int insertAudit(@Param("refundId") Long refundId,
                    @Param("refundNo") String refundNo,
                    @Param("eventType") String eventType,
                    @Param("fromStatus") String fromStatus,
                    @Param("toStatus") String toStatus,
                    @Param("actorType") String actorType,
                    @Param("actorId") Long actorId,
                    @Param("reason") String reason);
}
