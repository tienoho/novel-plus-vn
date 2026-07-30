package com.java2nb.novel.service.gift;

import java.util.List;

public record GiftRedemptionHistoryPage(List<GiftRedemptionHistoryRow> items, long total,
                                        int page, int pageSize) {
}
