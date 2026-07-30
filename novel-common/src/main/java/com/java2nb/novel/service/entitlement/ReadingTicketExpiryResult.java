package com.java2nb.novel.service.entitlement;

public record ReadingTicketExpiryResult(int lotCount, long ticketCount) {
    public static final ReadingTicketExpiryResult EMPTY = new ReadingTicketExpiryResult(0, 0);
}
