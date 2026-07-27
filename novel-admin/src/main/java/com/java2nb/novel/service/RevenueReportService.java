package com.java2nb.novel.service;

import com.java2nb.novel.common.vo.RevenueReportVO;

import java.util.Map;

public interface RevenueReportService {

    RevenueReportVO getRevenueSummary(String period, String startDate, String endDate);

    byte[] exportRevenueCsv(RevenueReportVO report);

    byte[] exportRevenueXlsx(RevenueReportVO report);

    byte[] exportRevenuePdf(RevenueReportVO report);

    byte[] exportRevenueJson(RevenueReportVO report);

    Map<String, Object> getPitSummaryReport(String startDate, String endDate);

    Map<String, Object> getVatSummaryReport(String startDate, String endDate);
}
