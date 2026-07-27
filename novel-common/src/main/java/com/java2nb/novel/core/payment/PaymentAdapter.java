package com.java2nb.novel.core.payment;

import java.util.Map;

/**
 * Interface dùng chung cho các Adapter cổng thanh toán (VNPAY, VietQR, NAPAS 247)
 */
public interface PaymentAdapter {

    /**
     * Mã kênh thanh toán (4: VNPAY, 5: VIETQR, 6: NAPAS_247)
     */
    byte getChannelCode();

    /**
     * Tên kênh thanh toán
     */
    String getChannelName();

    /**
     * Khởi tạo đơn nạp tiền
     */
    PaymentCreationResult createDepositOrder(PaymentCreationRequest request);

    /**
     * Xác thực và phân tích Webhook / IPN callback từ cổng thanh toán
     */
    WebhookVerifyResult verifyAndParseWebhook(Map<String, String> headers, Map<String, String> params, String body);

    /**
     * Tra cứu trạng thái đơn hàng phía cổng thanh toán
     */
    QueryOrderResult queryOrderStatus(String outTradeNo);

    /**
     * Thực hiện chuyển khoản chi trả (Payout)
     */
    PayoutResult processPayout(PayoutRequest request);
}
