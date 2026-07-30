package com.java2nb.novel.service.gamification;

/** Kết quả đóng các lot Ngọn Đuốc đã hết hạn của một người dùng trong một transaction. */
public record TicketExpiryResult(int lotCount, long ticketCount) {

    public static final TicketExpiryResult EMPTY = new TicketExpiryResult(0, 0);
}
