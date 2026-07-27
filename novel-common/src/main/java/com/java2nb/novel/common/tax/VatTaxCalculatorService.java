package com.java2nb.novel.common.tax;

import org.springframework.stereotype.Service;

/**
 * Service for calculating Value Added Tax (VAT / Thuế GTGT) on customer top-up recharges (VNPAY/VietQR).
 * Formula:
 * Gross = Net + VAT
 * Net = Gross / (1 + vatRate)
 * VAT = Gross - Net
 */
@Service
public class VatTaxCalculatorService {

    /**
     * Default standard VAT rate: 10% (0.10).
     */
    public static final double DEFAULT_VAT_RATE = 0.10;

    /**
     * Calculates VAT from gross VNPAY recharge amount using default rate (10%).
     *
     * @param grossAmountVnd Gross recharge amount in VND
     * @return VatTaxResult
     */
    public VatTaxResult calculateVat(long grossAmountVnd) {
        return calculateVat(grossAmountVnd, DEFAULT_VAT_RATE);
    }

    /**
     * Calculates VAT from gross amount with custom VAT rate.
     *
     * @param grossAmountVnd Gross recharge amount in VND
     * @param vatRate        VAT rate (e.g. 0.10)
     * @return VatTaxResult
     */
    public VatTaxResult calculateVat(long grossAmountVnd, double vatRate) {
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
