package com.java2nb.novel.service;

import java.util.Date;

public record PayOrderSnapshot(long id, long outTradeNo, int totalAmount, int accountAmount,
                               Date createTime, Date updateTime) {
}
