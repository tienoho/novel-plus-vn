package com.java2nb.novel.common.tax;

import org.springframework.stereotype.Component;

/**
 * Personal Income Tax (PIT / Thuế TNCN) & VAT calculation engine according to
 * Circular 111/2013/TT-BTC and Circular 92/2015/TT-BTC.
 */
@Component
public class PitTaxCalculator {

    public static final long PIT_EXEMPTION_THRESHOLD_VND = 2_000_000L;
    public static final double DEFAULT_PIT_RATE = 0.10;
    public static final double DEFAULT_VAT_RATE = 0.10;

    /**
     * Calculate Personal Income Tax (10% rate if author monthly withdrawal >= 2,000,000 VND).
     */
    public static PitTaxResult calculatePitTax(long grossAmountVnd) {
        return calculatePitTax(grossAmountVnd, PIT_EXEMPTION_THRESHOLD_VND, DEFAULT_PIT_RATE);
    }

    public static PitTaxResult calculatePitTax(long grossAmountVnd, long exemptionThresholdVnd, double taxRate) {
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

    /**
     * VAT separation engine for customer recharge payments.
     * Net = Gross / (1 + vatRate)
     * VAT = Gross - Net
     */
    public static VatTaxResult separateVat(long grossAmountVnd) {
        return separateVat(grossAmountVnd, DEFAULT_VAT_RATE);
    }

    public static VatTaxResult separateVat(long grossAmountVnd, double vatRate) {
        if (grossAmountVnd < 0) {
            throw new IllegalArgumentException("Gross amount cannot be negative");
        }
        if (vatRate < 0 || vatRate > 1.0) {
            throw new IllegalArgumentException("VAT rate must be between 0.0 and 1.0");
        }

        long netAmountVnd = Math.round((double) grossAmountVnd / (1.0 + vatRate));
        long vatAmountVnd = grossAmountVnd - netAmountVnd;

        return VatTaxResult.builder()
                .grossAmountVnd(grossAmountVnd)
                .vatAmountVnd(vatAmountVnd)
                .netAmountVnd(netAmountVnd)
                .vatRate(vatRate)
                .build();
    }
}
