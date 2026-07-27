package com.java2nb.novel.common.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevenueReportVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String period; // DAILY, MONTHLY, QUARTERLY, ANNUAL
    private String startDate;
    private String endDate;

    private long totalRechargeGrossVnd;
    private long totalRechargeVatVnd;
    private long totalRechargeNetVnd;

    private long totalXuIssued;
    private long totalXuConsumed;

    private long totalAuthorGrossEarningsVnd;
    private long totalAuthorPitWithheldVnd;
    private long totalAuthorPayoutDisbursedVnd;

    private long platformNetRevenueVnd;

    private List<RevenuePeriodItemVO> items;
}
