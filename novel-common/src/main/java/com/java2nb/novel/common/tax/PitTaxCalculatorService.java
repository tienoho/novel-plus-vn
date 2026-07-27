package com.java2nb.novel.common.tax;

import org.springframework.stereotype.Service;

/**
 * Service for calculating Personal Income Tax (PIT / Thuế TNCN) on author payouts
 * according to Vietnamese tax policy (Circular 111/2013/TT-BTC & Circular 92/2015/TT-BTC).
 */
@Service
public class PitTaxCalculatorService {

    /**
     * Default exemption threshold: 2,000,000 VND per transaction/payout cycle.
     */
    public static final long DEFAULT_EXEMPTION_THRESHOLD_VND = 2_000_000L;

    /**
     * Default withholding PIT rate: 10% (0.10).
     */
    public static final double DEFAULT_PIT_TAX_RATE = 0.10;

    /**
     * Calculates PIT using default exemption threshold (2,000,000 VND) and default rate (10%).
     *
     * @param grossAmountVnd Gross payout amount in VND
     * @return PitTaxResult containing tax metrics
     */
    public PitTaxResult calculatePit(long grossAmountVnd) {
        return calculatePit(grossAmountVnd, DEFAULT_EXEMPTION_THRESHOLD_VND, DEFAULT_PIT_TAX_RATE);
    }

    /**
     * Calculates PIT with custom threshold and tax rate.
     *
     * @param grossAmountVnd        Gross payout amount in VND
     * @param exemptionThresholdVnd Exemption threshold in VND
     * @param taxRate               Tax rate (e.g. 0.10)
     * @return PitTaxResult
     */
    public PitTaxResult calculatePit(long grossAmountVnd, long exemptionThresholdVnd, double taxRate) {
        if (grossAmountVnd < 0) {
            throw new IllegalArgumentException("Gross amount cannot be negative");
        }
        if (exemptionThresholdVnd < 0) {
            throw new IllegalArgumentException("Exemption threshold cannot be negative");
        }
        if (taxRate < 0 || taxRate > 1.0) {
            throw new IllegalArgumentException("Tax rate must be between 0.0 and 1.0");
        }

        long taxableAmountVnd = 0L;
        long withheldTaxVnd = 0L;

        if (grossAmountVnd > exemptionThresholdVnd) {
            taxableAmountVnd = grossAmountVnd;
            withheldTaxVnd = Math.round(grossAmountVnd * taxRate);
        }

        long netAmountVnd = grossAmountVnd - withheldTaxVnd;

        return PitTaxResult.builder()
                .grossAmountVnd(grossAmountVnd)
                .taxableAmountVnd(taxableAmountVnd)
                .withheldTaxVnd(withheldTaxVnd)
                .netAmountVnd(netAmountVnd)
                .taxRate(taxRate)
                .exemptionThresholdVnd(exemptionThresholdVnd)
                .build();
    }
}
