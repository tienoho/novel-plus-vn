package com.java2nb.novel.service;

import com.java2nb.novel.common.tax.PitTaxCalculatorService;
import com.java2nb.novel.common.tax.VatTaxCalculatorService;
import com.java2nb.novel.common.vo.RevenueReportVO;
import com.java2nb.novel.dao.RevenueReportDao;
import com.java2nb.novel.service.impl.RevenueReportServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class RevenueReportServiceTest {

    private RevenueReportDao revenueReportDao;
    private VatTaxCalculatorService vatCalculatorService;
    private PitTaxCalculatorService pitCalculatorService;
    private RevenueReportServiceImpl revenueReportService;

    @BeforeEach
    public void setUp() {
        revenueReportDao = mock(RevenueReportDao.class);
        vatCalculatorService = new VatTaxCalculatorService();
        pitCalculatorService = new PitTaxCalculatorService();
        revenueReportService = new RevenueReportServiceImpl(revenueReportDao, vatCalculatorService, pitCalculatorService);
    }

    @Test
    public void testGetRevenueSummary() {
        Date now = new Date();

        List<Map<String, Object>> recharges = new ArrayList<>();
        Map<String, Object> r1 = new HashMap<>();
        r1.put("createTime", now);
        r1.put("totalAmount", 110_000L);
        r1.put("accountAmount", 10_000L);
        recharges.add(r1);

        List<Map<String, Object>> payouts = new ArrayList<>();
        Map<String, Object> p1 = new HashMap<>();
        p1.put("createTime", now);
        p1.put("grossAmountVnd", 5_000_000L);
        p1.put("withheldTaxVnd", 500_000L);
        p1.put("netAmountVnd", 4_500_000L);
        payouts.add(p1);

        List<Map<String, Object>> xuConsumptions = new ArrayList<>();
        Map<String, Object> x1 = new HashMap<>();
        x1.put("createTime", now);
        x1.put("buyAmount", 2_000L);
        xuConsumptions.add(x1);

        when(revenueReportDao.selectRecharges("2026-07-01", "2026-07-31")).thenReturn(recharges);
        when(revenueReportDao.selectPayouts("2026-07-01", "2026-07-31")).thenReturn(payouts);
        when(revenueReportDao.selectXuConsumptions("2026-07-01", "2026-07-31")).thenReturn(xuConsumptions);

        RevenueReportVO report = revenueReportService.getRevenueSummary("DAILY", "2026-07-01", "2026-07-31");

        assertNotNull(report);
        assertEquals("DAILY", report.getPeriod());
        assertEquals(110_000L, report.getTotalRechargeGrossVnd());
        assertEquals(10_000L, report.getTotalRechargeVatVnd());
        assertEquals(100_000L, report.getTotalRechargeNetVnd());
        assertEquals(10_000L, report.getTotalXuIssued());
        assertEquals(2_000L, report.getTotalXuConsumed());
        assertEquals(5_000_000L, report.getTotalAuthorGrossEarningsVnd());
        assertEquals(500_000L, report.getTotalAuthorPitWithheldVnd());
        assertEquals(4_500_000L, report.getTotalAuthorPayoutDisbursedVnd());
        assertEquals(100_000L - 4_500_000L, report.getPlatformNetRevenueVnd());
        assertFalse(report.getItems().isEmpty());
    }

    @Test
    public void testPitAndVatSummaryReports() {
        List<Map<String, Object>> payouts = new ArrayList<>();
        Map<String, Object> p1 = new HashMap<>();
        p1.put("grossAmountVnd", 2_000_000L);
        p1.put("withheldTaxVnd", 200_000L);
        p1.put("netAmountVnd", 1_800_000L);
        payouts.add(p1);

        when(revenueReportDao.selectPayouts(null, null)).thenReturn(payouts);

        Map<String, Object> pitSummary = revenueReportService.getPitSummaryReport(null, null);
        assertNotNull(pitSummary);
        assertEquals(1, pitSummary.get("totalPayoutsCount"));
        assertEquals(200_000L, pitSummary.get("totalWithheldPitVnd"));

        List<Map<String, Object>> recharges = new ArrayList<>();
        Map<String, Object> r1 = new HashMap<>();
        r1.put("totalAmount", 220_000L);
        recharges.add(r1);

        when(revenueReportDao.selectRecharges(null, null)).thenReturn(recharges);

        Map<String, Object> vatSummary = revenueReportService.getVatSummaryReport(null, null);
        assertNotNull(vatSummary);
        assertEquals(1, vatSummary.get("totalRechargeCount"));
        assertEquals(20_000L, vatSummary.get("totalVatAmountVnd"));
    }

    @Test
    public void testReportExportsCsvXlsxPdfJson() {
        RevenueReportVO report = RevenueReportVO.builder()
                .period("DAILY")
                .startDate("2026-07-01")
                .endDate("2026-07-31")
                .totalRechargeGrossVnd(110_000L)
                .totalRechargeVatVnd(10_000L)
                .totalRechargeNetVnd(100_000L)
                .totalXuIssued(1000L)
                .totalXuConsumed(500L)
                .totalAuthorGrossEarningsVnd(50_000L)
                .totalAuthorPitWithheldVnd(5_000L)
                .totalAuthorPayoutDisbursedVnd(45_000L)
                .platformNetRevenueVnd(55_000L)
                .items(Collections.emptyList())
                .build();

        byte[] csv = revenueReportService.exportRevenueCsv(report);
        assertNotNull(csv);
        assertTrue(new String(csv, StandardCharsets.UTF_8).contains("Khoi-Thu"));

        byte[] xlsx = revenueReportService.exportRevenueXlsx(report);
        assertNotNull(xlsx);
        assertTrue(xlsx.length > 0);

        byte[] pdf = revenueReportService.exportRevenuePdf(report);
        assertNotNull(pdf);
        assertTrue(new String(pdf, StandardCharsets.ISO_8859_1).startsWith("%PDF-1.4"));

        byte[] json = revenueReportService.exportRevenueJson(report);
        assertNotNull(json);
        assertTrue(new String(json, StandardCharsets.UTF_8).contains("totalRechargeGrossVnd"));
    }
}
