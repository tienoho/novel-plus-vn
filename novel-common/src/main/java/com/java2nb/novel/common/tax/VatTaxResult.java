package com.java2nb.novel.common.tax;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result object for Value Added Tax (VAT / Thuế GTGT) calculation on customer top-ups / recharges.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VatTaxResult {

    /**
     * Gross top-up amount paid by user via payment gateway (VND).
     */
    private long grossAmountVnd;

    /**
     * VAT portion included in the gross payment (VND).
     */
    private long vatAmountVnd;

    /**
     * Net platform revenue after VAT separation (VND).
     */
    private long netAmountVnd;

    /**
     * VAT rate applied (e.g. 0.10 for 10%).
     */
    private double vatRate;
}
