package com.java2nb.novel.service.gift;

import java.util.List;

public record GiftCodePage(List<GiftCodeRow> items, long total, int page, int pageSize) {
}
