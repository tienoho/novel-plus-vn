package com.java2nb.novel.service.entitlement;

public class InsufficientReadingTicketException extends RuntimeException {
    public InsufficientReadingTicketException() {
        super("Không đủ Vé đọc để mở chương");
    }
}
