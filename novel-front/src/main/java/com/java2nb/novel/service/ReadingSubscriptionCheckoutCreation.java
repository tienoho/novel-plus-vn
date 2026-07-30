package com.java2nb.novel.service;

import java.util.Date;

public record ReadingSubscriptionCheckoutCreation(long outTradeNo, int amountVnd,
                                                  Date createTime, boolean replay) {
    public ReadingSubscriptionCheckoutCreation {
        createTime = new Date(createTime.getTime());
    }

    @Override public Date createTime() { return new Date(createTime.getTime()); }
}
