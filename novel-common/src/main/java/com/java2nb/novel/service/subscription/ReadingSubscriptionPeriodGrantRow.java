package com.java2nb.novel.service.subscription;

import lombok.Data;

import java.util.Date;

@Data
public class ReadingSubscriptionPeriodGrantRow {
    private Long id;
    private Long subscriptionId;
    private Long userId;
    private Date periodStart;
    private Date periodEnd;
    private Long ticketAmount;
    private Date ticketExpireAt;
    private Date createTime;
}
