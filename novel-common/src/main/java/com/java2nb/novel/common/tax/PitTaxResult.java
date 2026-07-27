package com.java2nb.novel.common.tax;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result object for Personal Income Tax (PIT / Thuế TNCN) calculation.
 * Standard threshold: 2,000,000 VND, standard rate: 10% per Circular 111/2013/TT-BTC.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PitTaxResult {

    /**
     * Gross payout amount before tax (VND).
     */
    private long grossAmountVnd;

    /**
     * Amount subject to tax withholding (VND).
     */
    private long taxableAmountVnd;

    /**
     * Withheld Personal Income Tax amount (VND).
     */
    private long withheldTaxVnd;

    /**
     * Net payout amount after tax withholding (VND).
     */
    private long netAmountVnd;

    /**
     * Tax rate applied (e.g. 0.10 for 10%).
     */
    private double taxRate;

    /**
     * Tax exemption threshold (e.g. 2,000,000 VND).
     */
    private long exemptionThresholdVnd;
}
