package com.java2nb.novel.service.finance;

public class PiiEncryptionUnavailableException extends RuntimeException {

    public PiiEncryptionUnavailableException() {
        super("Chưa cấu hình khóa mã hóa dữ liệu định danh");
    }
}
