package com.java2nb.novel.service.entitlement;

import java.util.List;

public record ReadingTicketLotPage(List<ReadingTicketLotHistoryRow> items, long total, int page, int pageSize) {
}
