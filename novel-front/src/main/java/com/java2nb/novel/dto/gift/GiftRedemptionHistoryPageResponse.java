package com.java2nb.novel.dto.gift;

import com.java2nb.novel.service.gift.GiftRedemptionHistoryPage;

import java.util.List;

public record GiftRedemptionHistoryPageResponse(List<GiftRedemptionHistoryItemResponse> items,
                                                long total, int page, int pageSize) {
    public static GiftRedemptionHistoryPageResponse from(GiftRedemptionHistoryPage source) {
        return new GiftRedemptionHistoryPageResponse(source.items().stream()
            .map(GiftRedemptionHistoryItemResponse::from).toList(), source.total(),
            source.page(), source.pageSize());
    }
}
