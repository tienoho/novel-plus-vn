package com.java2nb.novel.common.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevenuePeriodItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String periodLabel; // e.g. "2026-07-25" or "2026-07" or "2026-Q3" or "2026"
    private long rechargeGrossVnd;
    private long rechargeVatVnd;
    private long rechargeNetVnd;
    private long xuConsumed;
    private long authorGrossEarningsVnd;
    private long authorPitWithheldVnd;
    private long authorPayoutDisbursedVnd;
    private long platformNetRevenueVnd;
}
