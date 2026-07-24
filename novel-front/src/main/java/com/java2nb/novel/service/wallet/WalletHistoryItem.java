package com.java2nb.novel.service.wallet;

import lombok.Data;

import java.util.Date;

@Data
public class WalletHistoryItem {

    private String transactionNo;
    private String businessType;
    private String businessId;
    private Long amount;
    private Long balanceAfter;
    private String description;
    private Date createTime;
}
