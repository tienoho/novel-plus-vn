package com.java2nb.novel.service.wallet;

public class InsufficientWalletBalanceException extends RuntimeException {

    public InsufficientWalletBalanceException() {
        super("Số dư ví không đủ");
    }
}
