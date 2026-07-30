package com.java2nb.novel.service.subscription;

import java.util.List;

public record ReadingSubscriptionPurchasePage(
    List<ReadingSubscriptionPurchaseRow> items,
    long total,
    int page,
    int pageSize) {
}
