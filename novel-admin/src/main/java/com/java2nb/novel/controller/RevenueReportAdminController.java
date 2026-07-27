package com.java2nb.novel.controller;

import com.java2nb.common.utils.R;
import com.java2nb.novel.common.vo.RevenueReportVO;
import com.java2nb.novel.service.RevenueReportService;
import lombok.RequiredArgsConstructor;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/novel/reports")
@RequiredArgsConstructor
public class RevenueReportAdminController {

    private final RevenueReportService revenueReportService;

    @GetMapping("/revenue/summary")
    @RequiresPermissions("novel:reports:revenue")
    public R getRevenueSummary(@RequestParam(defaultValue = "DAILY") String period,
                               @RequestParam(required = false) String startDate,
                               @RequestParam(required = false) String endDate) {
        RevenueReportVO summary = revenueReportService.getRevenueSummary(period, startDate, endDate);
        return R.ok().put("data", summary);
    }

    @GetMapping("/revenue/export/csv")
    @RequiresPermissions("novel:reports:export")
    public ResponseEntity<byte[]> exportCsv(@RequestParam(defaultValue = "DAILY") String period,
                                            @RequestParam(required = false) String startDate,
                                            @RequestParam(required = false) String endDate) {
        RevenueReportVO summary = revenueReportService.getRevenueSummary(period, startDate, endDate);
        byte[] csvData = revenueReportService.exportRevenueCsv(summary);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"revenue_report.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csvData);
    }

    @GetMapping("/revenue/export/xlsx")
    @RequiresPermissions("novel:reports:export")
    public ResponseEntity<byte[]> exportXlsx(@RequestParam(defaultValue = "DAILY") String period,
                                             @RequestParam(required = false) String startDate,
                                             @RequestParam(required = false) String endDate) {
        RevenueReportVO summary = revenueReportService.getRevenueSummary(period, startDate, endDate);
        byte[] xlsxData = revenueReportService.exportRevenueXlsx(summary);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"revenue_report.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(xlsxData);
    }

    @GetMapping("/revenue/export/pdf")
    @RequiresPermissions("novel:reports:export")
    public ResponseEntity<byte[]> exportPdf(@RequestParam(defaultValue = "DAILY") String period,
                                            @RequestParam(required = false) String startDate,
                                            @RequestParam(required = false) String endDate) {
        RevenueReportVO summary = revenueReportService.getRevenueSummary(period, startDate, endDate);
        byte[] pdfData = revenueReportService.exportRevenuePdf(summary);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"revenue_statement.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfData);
    }

    @GetMapping("/revenue/export/json")
    @RequiresPermissions("novel:reports:export")
    public ResponseEntity<byte[]> exportJson(@RequestParam(defaultValue = "DAILY") String period,
                                              @RequestParam(required = false) String startDate,
                                              @RequestParam(required = false) String endDate) {
        RevenueReportVO summary = revenueReportService.getRevenueSummary(period, startDate, endDate);
        byte[] jsonData = revenueReportService.exportRevenueJson(summary);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"revenue_report.json\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsonData);
    }

    @GetMapping("/tax/pit-summary")
    @RequiresPermissions("novel:reports:revenue")
    public R getPitSummary(@RequestParam(required = false) String startDate,
                           @RequestParam(required = false) String endDate) {
        Map<String, Object> pitSummary = revenueReportService.getPitSummaryReport(startDate, endDate);
        return R.ok().put("data", pitSummary);
    }

    @GetMapping("/tax/vat-summary")
    @RequiresPermissions("novel:reports:revenue")
    public R getVatSummary(@RequestParam(required = false) String startDate,
                           @RequestParam(required = false) String endDate) {
        Map<String, Object> vatSummary = revenueReportService.getVatSummaryReport(startDate, endDate);
        return R.ok().put("data", vatSummary);
    }
}
