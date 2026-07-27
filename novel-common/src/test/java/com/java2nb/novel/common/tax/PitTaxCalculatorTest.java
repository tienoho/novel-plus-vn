package com.java2nb.novel.common.tax;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PitTaxCalculatorTest {

    private PitTaxCalculatorService pitService;
    private VatTaxCalculatorService vatService;

    @BeforeEach
    public void setUp() {
        pitService = new PitTaxCalculatorService();
        vatService = new VatTaxCalculatorService();
    }

    @Test
    public void testPitCalculationBelowThreshold() {
        // Below 2,000,000 VND -> Exemption threshold not reached, 0 tax
        long gross = 1_500_000L;
        PitTaxResult result = pitService.calculatePit(gross);

        assertEquals(1_500_000L, result.getGrossAmountVnd());
        assertEquals(0L, result.getTaxableAmountVnd());
        assertEquals(0L, result.getWithheldTaxVnd());
        assertEquals(1_500_000L, result.getNetAmountVnd());
    }

    @Test
    public void testPitCalculationAtThreshold() {
        // Exactly 2,000,000 VND -> Exempt (tax = 0)
        long gross = 2_000_000L;
        PitTaxResult result = pitService.calculatePit(gross);

        assertEquals(2_000_000L, result.getGrossAmountVnd());
        assertEquals(0L, result.getTaxableAmountVnd());
        assertEquals(0L, result.getWithheldTaxVnd());
        assertEquals(2_000_000L, result.getNetAmountVnd());
    }

    @Test
    public void testPitCalculationExceedingThreshold() {
        // 5,000,000 VND -> 10% tax = 500,000 VND, net = 4,500,000 VND
        long gross = 5_000_000L;
        PitTaxResult result = pitService.calculatePit(gross);

        assertEquals(5_000_000L, result.getGrossAmountVnd());
        assertEquals(5_000_000L, result.getTaxableAmountVnd());
        assertEquals(500_000L, result.getWithheldTaxVnd());
        assertEquals(4_500_000L, result.getNetAmountVnd());
    }

    @Test
    public void testStaticPitTaxCalculator() {
        // Test PitTaxCalculator static methods
        PitTaxResult pitResult = PitTaxCalculator.calculatePitTax(10_000_000L);
        assertEquals(1_000_000L, pitResult.getWithheldTaxVnd());
        assertEquals(9_000_000L, pitResult.getNetAmountVnd());

        VatTaxResult vatResult = PitTaxCalculator.separateVat(110_000L);
        assertEquals(100_000L, vatResult.getNetAmountVnd());
        assertEquals(10_000L, vatResult.getVatAmountVnd());
    }

    @Test
    public void testVatCalculation() {
        // Gross 110,000 VND with 10% VAT -> Net 100,000 VND, VAT 10,000 VND
        long gross = 110_000L;
        VatTaxResult result = vatService.calculateVat(gross);

        assertEquals(110_000L, result.getGrossAmountVnd());
        assertEquals(100_000L, result.getNetAmountVnd());
        assertEquals(10_000L, result.getVatAmountVnd());
        assertEquals(0.10, result.getVatRate());
    }

    @Test
    public void testInvalidInputsThrowException() {
        assertThrows(IllegalArgumentException.class, () -> pitService.calculatePit(-100L));
        assertThrows(IllegalArgumentException.class, () -> pitService.calculatePit(100L, -1L, 0.10));
        assertThrows(IllegalArgumentException.class, () -> pitService.calculatePit(100L, 1000L, 1.5));
        assertThrows(IllegalArgumentException.class, () -> vatService.calculateVat(-500L));
    }
}
