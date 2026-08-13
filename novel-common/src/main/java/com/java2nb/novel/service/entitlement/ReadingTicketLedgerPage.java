package com.java2nb.novel.service.entitlement;

import java.util.List;

public record ReadingTicketLedgerPage(List<ReadingTicketLedgerRow> items, long total, int page, int pageSize) {
}
