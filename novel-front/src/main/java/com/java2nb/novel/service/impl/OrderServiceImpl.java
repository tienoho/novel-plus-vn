package com.java2nb.novel.service.impl;

import com.java2nb.novel.entity.OrderPay;
import com.java2nb.novel.mapper.OrderPayDynamicSqlSupport;
import com.java2nb.novel.mapper.OrderPayMapper;
import com.java2nb.novel.service.OrderService;
import com.java2nb.novel.service.PayOrderCreation;
import com.java2nb.novel.service.PayOrderSnapshot;
import com.java2nb.novel.service.PayOrderState;
import com.java2nb.novel.service.PayOrderUpdateResult;
import com.java2nb.novel.service.UserService;
import lombok.RequiredArgsConstructor;
import org.mybatis.dynamic.sql.render.RenderingStrategies;
import org.mybatis.dynamic.sql.select.render.SelectStatementProvider;
import org.mybatis.dynamic.sql.update.render.UpdateStatementProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Date;
import java.util.List;

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

    private final UserService userService;


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
    public PayOrderState inspectPayOrder(Long outTradeNo, byte payChannel, int totalAmount) {
        OrderPay orderPay = selectPayOrder(outTradeNo);
        if (orderPay == null) {
            return PayOrderState.NOT_FOUND;
        }
        if (orderPay.getPayChannel() == null || orderPay.getPayChannel() != payChannel) {
            return PayOrderState.INVALID_CHANNEL;
        }
        if (orderPay.getTotalAmount() == null || orderPay.getTotalAmount() != totalAmount) {
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
            .and(OrderPayDynamicSqlSupport.accountAmount, isGreaterThan(0))
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
        if (successful && (orderPay.getAccountAmount() == null || orderPay.getAccountAmount() <= 0)) {
            return PayOrderUpdateResult.INVALID_ACCOUNT_AMOUNT;
        }
        if (orderPay.getPayStatus() == null || orderPay.getPayStatus() != 2) {
            return PayOrderUpdateResult.ALREADY_PROCESSED;
        }

        UpdateStatementProvider updateStatement = update(OrderPayDynamicSqlSupport.orderPay)
            .set(OrderPayDynamicSqlSupport.tradeNo).equalTo(tradeNo)
            .set(OrderPayDynamicSqlSupport.payStatus).equalTo(successful ? (byte) 1 : (byte) 0)
            .set(OrderPayDynamicSqlSupport.updateTime).equalTo(new Date())
            .where(OrderPayDynamicSqlSupport.id, isEqualTo(orderPay.getId()))
            .and(OrderPayDynamicSqlSupport.payStatus, isEqualTo((byte) 2))
            .build()
            .render(RenderingStrategies.MYBATIS3);
        if (orderPayMapper.update(updateStatement) == 0) {
            return PayOrderUpdateResult.ALREADY_PROCESSED;
        }
        if (successful) {
            if (!userService.addAmount(orderPay.getUserId(), orderPay.getAccountAmount())) {
                throw new IllegalStateException("Không thể cập nhật số dư cho người dùng của đơn thanh toán");
            }
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

    private long nextOrderNumber() {
        long candidate;
        do {
            candidate = ORDER_NUMBER_RANDOM.nextLong() & Long.MAX_VALUE;
        } while (candidate == 0);
        return candidate;
    }
}
