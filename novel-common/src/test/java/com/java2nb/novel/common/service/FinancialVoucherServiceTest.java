package com.java2nb.novel.common.service;

import com.java2nb.novel.common.dao.FinancialVoucherDao;
import com.java2nb.novel.common.entity.FinancialVoucherDO;
import com.java2nb.novel.common.service.impl.FinancialVoucherServiceImpl;
import com.java2nb.novel.common.tax.PitTaxCalculatorService;
import com.java2nb.novel.common.tax.VatTaxCalculatorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class FinancialVoucherServiceTest {

    private FinancialVoucherDao voucherDao;
    private VatTaxCalculatorService vatCalculatorService;
    private PitTaxCalculatorService pitCalculatorService;
    private FinancialVoucherServiceImpl voucherService;

    @BeforeEach
    public void setUp() {
        voucherDao = mock(FinancialVoucherDao.class);
        vatCalculatorService = new VatTaxCalculatorService();
        pitCalculatorService = new PitTaxCalculatorService();
        voucherService = new FinancialVoucherServiceImpl(voucherDao, vatCalculatorService, pitCalculatorService);
        ReflectionTestUtils.setField(voucherService, "issuanceEnabled", true);
        ReflectionTestUtils.setField(voucherService, "platformLegalName", "Công ty kiểm thử Novel Plus");
        ReflectionTestUtils.setField(voucherService, "platformTaxCode", "0123456789");
    }

    @Test
    public void testIssuanceIsDisabledByDefaultConfiguration() {
        when(voucherDao.selectByReference("ORDER_PAY", "ORD-DISABLED")).thenReturn(null);
        ReflectionTestUtils.setField(voucherService, "issuanceEnabled", false);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> voucherService.createRechargeReceipt("ORD-DISABLED", "Nguyen Van A", null, 110_000L));

        assertEquals("Phát hành chứng từ tài chính đang bị tắt", exception.getMessage());
        verify(voucherDao, never()).insert(any(FinancialVoucherDO.class));
    }

    @Test
    public void testCreateRechargeReceipt() {
        when(voucherDao.selectByReference("ORDER_PAY", "ORD-1001")).thenReturn(null);

        FinancialVoucherDO voucher = voucherService.createRechargeReceipt("ORD-1001", "Nguyen Van A", "123456789", 110_000L);

        assertNotNull(voucher);
        assertTrue(voucher.getVoucherNo().startsWith("INV-"));
        assertEquals("RECHARGE_RECEIPT", voucher.getVoucherType());
        assertEquals("ORDER_PAY", voucher.getReferenceType());
        assertEquals("ORD-1001", voucher.getReferenceId());
        assertEquals(110_000L, voucher.getGrossAmountVnd());
        assertEquals(10_000L, voucher.getTaxAmountVnd()); // 10% VAT
        assertEquals(100_000L, voucher.getNetAmountVnd());

        verify(voucherDao, times(1)).insert(any(FinancialVoucherDO.class));
    }

    @Test
    public void testCreatePayoutVoucher() {
        when(voucherDao.selectByReference("AUTHOR_WITHDRAWAL_REQUEST", "500")).thenReturn(null);

        FinancialVoucherDO voucher = voucherService.createPayoutVoucher(500L, "Author PenName", "987654321", 5_000_000L, 500_000L);

        assertNotNull(voucher);
        assertTrue(voucher.getVoucherNo().startsWith("VOUCHER-"));
        assertEquals("AUTHOR_PAYOUT_VOUCHER", voucher.getVoucherType());
        assertEquals("500", voucher.getReferenceId());
        assertEquals(5_000_000L, voucher.getGrossAmountVnd());
        assertEquals(500_000L, voucher.getTaxAmountVnd()); // 10% PIT
        assertEquals(4_500_000L, voucher.getNetAmountVnd());

        verify(voucherDao, times(1)).insert(any(FinancialVoucherDO.class));
    }

    @Test
    public void testCreateRechargeReceiptIdempotency() {
        FinancialVoucherDO existing = FinancialVoucherDO.builder()
                .voucherNo("INV-20260725-12345")
                .referenceType("ORDER_PAY")
                .referenceId("ORD-1001")
                .build();
        when(voucherDao.selectByReference("ORDER_PAY", "ORD-1001")).thenReturn(existing);

        FinancialVoucherDO voucher = voucherService.createRechargeReceipt("ORD-1001", "Nguyen Van A", null, 110_000L);

        assertEquals("INV-20260725-12345", voucher.getVoucherNo());
        verify(voucherDao, never()).insert(any(FinancialVoucherDO.class));
    }

    @Test
    public void testExportVoucherPdf() {
        FinancialVoucherDO voucher = FinancialVoucherDO.builder()
                .voucherNo("INV-20260725-99999")
                .voucherType("RECHARGE_RECEIPT")
                .referenceType("ORDER_PAY")
                .referenceId("ORD-8888")
                .payerName("Test Payer")
                .payeeName("Novel-Plus")
                .grossAmountVnd(110_000L)
                .taxAmountVnd(10_000L)
                .netAmountVnd(100_000L)
                .status("ISSUED")
                .issuedAt(new Date())
                .build();
        when(voucherDao.selectByVoucherNo("INV-20260725-99999")).thenReturn(voucher);

        byte[] pdfBytes = voucherService.exportVoucherPdf("INV-20260725-99999");
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
        String pdfStr = new String(pdfBytes, StandardCharsets.ISO_8859_1);
        assertTrue(pdfStr.startsWith("%PDF-1.4"));
    }

    @Test
    public void testExportVouchersCsvAndJson() {
        List<FinancialVoucherDO> list = new ArrayList<>();
        list.add(FinancialVoucherDO.builder()
                .voucherNo("INV-20260725-11111")
                .voucherType("RECHARGE_RECEIPT")
                .referenceType("ORDER_PAY")
                .referenceId("ORD-1")
                .payerName("User A")
                .payeeName("Novel-Plus")
                .grossAmountVnd(220_000L)
                .taxAmountVnd(20_000L)
                .netAmountVnd(200_000L)
                .status("ISSUED")
                .issuedAt(new Date())
                .build());

        byte[] csvBytes = voucherService.exportVouchersCsv(list);
        assertNotNull(csvBytes);
        assertTrue(csvBytes.length > 3);
        // Verify UTF-8 BOM
        assertEquals((byte) 0xEF, csvBytes[0]);
        assertEquals((byte) 0xBB, csvBytes[1]);
        assertEquals((byte) 0xBF, csvBytes[2]);

        String csvStr = new String(csvBytes, StandardCharsets.UTF_8);
        assertTrue(csvStr.contains("Voucher No,Voucher Type"));
        assertTrue(csvStr.contains("INV-20260725-11111"));

        byte[] jsonBytes = voucherService.exportVouchersJson(list);
        assertNotNull(jsonBytes);
        String jsonStr = new String(jsonBytes, StandardCharsets.UTF_8);
        assertTrue(jsonStr.contains("INV-20260725-11111"));

        byte[] singleJson = voucherService.exportVoucherJson(list.get(0));
        assertNotNull(singleJson);
        assertTrue(new String(singleJson, StandardCharsets.UTF_8).contains("ORDER_PAY"));
    }
}
