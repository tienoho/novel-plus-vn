package com.java2nb.novel.service;

public enum PayOrderUpdateResult {
    SUCCESS,
    ALREADY_PROCESSED,
    NOT_FOUND,
    INVALID_CHANNEL,
    INVALID_AMOUNT,
    INVALID_ACCOUNT_AMOUNT
}
