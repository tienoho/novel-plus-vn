package com.java2nb.novel.dto.subscription;

public record ReadingSubscriptionCheckoutResponse(long outTradeNo, String status,
                                                   String paymentUrl, String qrCodeData,
                                                   String qrImageUrl, boolean replay) {
}
