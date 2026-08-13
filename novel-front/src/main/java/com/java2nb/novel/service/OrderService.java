package com.java2nb.novel.service;

import com.java2nb.novel.service.subscription.ReadingSubscriptionCheckoutOptions;
import java.util.Date;
import java.util.List;

/**
 * @author 11797
 */
public interface OrderService {


    /**
     * Tạo đơn nạp Xu
     *
     * @param payChannel kênh thanh toán
     * @param payAmount số tiền thanh toán
     * @param accountAmount số Xu cam kết cấp cho người dùng
     * @param userId ID người dùng
     * @return mã đơn thương nhân
     */
    PayOrderCreation createPayOrder(Byte payChannel, Integer payAmount, Integer accountAmount, Long userId);

    /** Tạo đơn mua một kỳ thuê bao từ snapshot plan phía server. */
    default ReadingSubscriptionCheckoutCreation createSubscriptionCheckout(
        byte payChannel, long userId, String planCode, String clientRequestId) {
        return createSubscriptionCheckout(payChannel, userId, planCode, clientRequestId,
            ReadingSubscriptionCheckoutOptions.oneOff(1));
    }

    ReadingSubscriptionCheckoutCreation createSubscriptionCheckout(
        byte payChannel, long userId, String planCode, String clientRequestId,
        ReadingSubscriptionCheckoutOptions options);

    /**
     * Đối chiếu dữ liệu VNPAY trả về với đơn đã lưu mà không thay đổi trạng thái đơn.
     */
    PayOrderState inspectPayOrder(Long outTradeNo, byte payChannel, int totalAmount);

    /**
     * Lấy một lô đơn chờ đủ tuổi để đối soát với cổng thanh toán.
     */
    List<PayOrderSnapshot> listPendingPayOrders(byte payChannel, Date createdAfter, Date createdBefore,
                                                Date updatedBefore, int limit);

    /**
     * Giành quyền đối soát một đơn bằng optimistic update để nhiều replica không gọi QueryDr trùng nhau.
     */
    boolean claimPendingPayOrder(long id, Date expectedUpdateTime, Date claimedAt);


    /**
     * Xử lý kết quả thanh toán đã được cổng thanh toán xác thực.
     */
    PayOrderUpdateResult processPayOrder(Long outTradeNo, String tradeNo, byte payChannel, int totalAmount,
                                         boolean successful);
}
