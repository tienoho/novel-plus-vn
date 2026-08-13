package com.java2nb.novel.service.gamification;

import java.util.List;

public record TicketHistoryPage(List<TicketLedgerRow> items, long total, int page, int pageSize) {
}
