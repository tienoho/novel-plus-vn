package com.java2nb.novel.service.wallet;

import java.util.List;

public record WalletHistoryPage(List<WalletHistoryItem> items, long total, int page, int pageSize) {
}
