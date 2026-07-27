package com.java2nb.novel.service.impl;

import com.java2nb.novel.common.tax.PitTaxCalculatorService;
import com.java2nb.novel.common.tax.VatTaxCalculatorService;
import com.java2nb.novel.common.tax.VatTaxResult;
import com.java2nb.novel.common.vo.RevenuePeriodItemVO;
import com.java2nb.novel.common.vo.RevenueReportVO;
import com.java2nb.novel.dao.RevenueReportDao;
import com.java2nb.novel.service.RevenueReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class RevenueReportServiceImpl implements RevenueReportService {

    private final RevenueReportDao revenueReportDao;
    private final VatTaxCalculatorService vatCalculatorService;
    private final PitTaxCalculatorService pitCalculatorService;

    @Override
    public RevenueReportVO getRevenueSummary(String period, String startDate, String endDate) {
        String periodType = StringUtils.defaultIfBlank(period, "DAILY").toUpperCase();

        List<Map<String, Object>> recharges = revenueReportDao.selectRecharges(startDate, endDate);
        List<Map<String, Object>> payouts = revenueReportDao.selectPayouts(startDate, endDate);
        List<Map<String, Object>> xuConsumptions = revenueReportDao.selectXuConsumptions(startDate, endDate);

        Map<String, RevenuePeriodItemVO> periodMap = new TreeMap<>();

        long totalRechargeGross = 0;
        long totalRechargeVat = 0;
        long totalRechargeNet = 0;
        long totalXuIssued = 0;
        long totalXuConsumed = 0;
        long totalAuthorGross = 0;
        long totalAuthorPit = 0;
        long totalAuthorNetPayout = 0;

        // Process Recharges
        for (Map<String, Object> r : recharges) {
            Date createTime = (Date) r.get("createTime");
            long gross = r.get("totalAmount") != null ? ((Number) r.get("totalAmount")).longValue() : 0;
            long accountAmount = r.get("accountAmount") != null ? ((Number) r.get("accountAmount")).longValue() : 0;

            VatTaxResult vat = vatCalculatorService.calculateVat(gross);
            totalRechargeGross += gross;
            totalRechargeVat += vat.getVatAmountVnd();
            totalRechargeNet += vat.getNetAmountVnd();
            totalXuIssued += accountAmount;

            String label = getPeriodLabel(createTime, periodType);
            RevenuePeriodItemVO item = periodMap.computeIfAbsent(label, k -> RevenuePeriodItemVO.builder().periodLabel(k).build());
            item.setRechargeGrossVnd(item.getRechargeGrossVnd() + gross);
            item.setRechargeVatVnd(item.getRechargeVatVnd() + vat.getVatAmountVnd());
            item.setRechargeNetVnd(item.getRechargeNetVnd() + vat.getNetAmountVnd());
        }

        // Process Payouts
        for (Map<String, Object> p : payouts) {
            Date createTime = (Date) p.get("createTime");
            long gross = p.get("grossAmountVnd") != null ? ((Number) p.get("grossAmountVnd")).longValue() : 0;
            long tax = p.get("withheldTaxVnd") != null ? ((Number) p.get("withheldTaxVnd")).longValue() : 0;
            long net = p.get("netAmountVnd") != null ? ((Number) p.get("netAmountVnd")).longValue() : 0;

            totalAuthorGross += gross;
            totalAuthorPit += tax;
            totalAuthorNetPayout += net;

            String label = getPeriodLabel(createTime, periodType);
            RevenuePeriodItemVO item = periodMap.computeIfAbsent(label, k -> RevenuePeriodItemVO.builder().periodLabel(k).build());
            item.setAuthorGrossEarningsVnd(item.getAuthorGrossEarningsVnd() + gross);
            item.setAuthorPitWithheldVnd(item.getAuthorPitWithheldVnd() + tax);
            item.setAuthorPayoutDisbursedVnd(item.getAuthorPayoutDisbursedVnd() + net);
        }

        // Process Xu Consumptions
        for (Map<String, Object> xc : xuConsumptions) {
            Date createTime = (Date) xc.get("createTime");
            long buyAmount = xc.get("buyAmount") != null ? ((Number) xc.get("buyAmount")).longValue() : 0;
            totalXuConsumed += buyAmount;

            String label = getPeriodLabel(createTime, periodType);
            RevenuePeriodItemVO item = periodMap.computeIfAbsent(label, k -> RevenuePeriodItemVO.builder().periodLabel(k).build());
            item.setXuConsumed(item.getXuConsumed() + buyAmount);
        }

        // Calculate Platform Net Revenue for each item
        List<RevenuePeriodItemVO> itemList = new ArrayList<>();
        for (RevenuePeriodItemVO item : periodMap.values()) {
            item.setPlatformNetRevenueVnd(item.getRechargeNetVnd() - item.getAuthorPayoutDisbursedVnd());
            itemList.add(item);
        }

        long platformNetRevenue = totalRechargeNet - totalAuthorNetPayout;

        return RevenueReportVO.builder()
                .period(periodType)
                .startDate(startDate)
                .endDate(endDate)
                .totalRechargeGrossVnd(totalRechargeGross)
                .totalRechargeVatVnd(totalRechargeVat)
                .totalRechargeNetVnd(totalRechargeNet)
                .totalXuIssued(totalXuIssued)
                .totalXuConsumed(totalXuConsumed)
                .totalAuthorGrossEarningsVnd(totalAuthorGross)
                .totalAuthorPitWithheldVnd(totalAuthorPit)
                .totalAuthorPayoutDisbursedVnd(totalAuthorNetPayout)
                .platformNetRevenueVnd(platformNetRevenue)
                .items(itemList)
                .build();
    }

    @Override
    public byte[] exportRevenueCsv(RevenueReportVO report) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8))) {
            // Write UTF-8 BOM
            baos.write(0xEF);
            baos.write(0xBB);
            baos.write(0xBF);

            writer.printf("BAO CAO DOANH THU THONG KE - Novel-Plus\n");
            writer.printf("Ky bao cao: %s, Tu ngay: %s, Den ngay: %s\n\n", report.getPeriod(), report.getStartDate(), report.getEndDate());

            writer.printf("TONG QUAN TAI CHINH\n");
            writer.printf("Tong nạp VNPAY (Gross): %d VND\n", report.getTotalRechargeGrossVnd());
            writer.printf("Tong thue VAT (10%%): %d VND\n", report.getTotalRechargeVatVnd());
            writer.printf("Tong nap ròng (Net): %d VND\n", report.getTotalRechargeNetVnd());
            writer.printf("Tong Xu phat hanh: %d\n", report.getTotalXuIssued());
            writer.printf("Tong Xu tieu dung: %d\n", report.getTotalXuConsumed());
            writer.printf("Tong thunhap tac gia (Gross): %d VND\n", report.getTotalAuthorGrossEarningsVnd());
            writer.printf("Tong thue TNCN khau tru (10%%): %d VND\n", report.getTotalAuthorPitWithheldVnd());
            writer.printf("Tong chi payout tac gia (Net): %d VND\n", report.getTotalAuthorPayoutDisbursedVnd());
            writer.printf("Loi nhuan rong nen tang: %d VND\n\n", report.getPlatformNetRevenueVnd());

            writer.println("Period,Gross Recharge (VND),VAT (VND),Net Recharge (VND),Xu Consumed,Author Gross (VND),PIT Withheld (VND),Author Net Payout (VND),Platform Net Revenue (VND)");

            for (RevenuePeriodItemVO item : report.getItems()) {
                writer.printf("\"%s\",%d,%d,%d,%d,%d,%d,%d,%d\n",
                        item.getPeriodLabel(),
                        item.getRechargeGrossVnd(),
                        item.getRechargeVatVnd(),
                        item.getRechargeNetVnd(),
                        item.getXuConsumed(),
                        item.getAuthorGrossEarningsVnd(),
                        item.getAuthorPitWithheldVnd(),
                        item.getAuthorPayoutDisbursedVnd(),
                        item.getPlatformNetRevenueVnd()
                );
            }
            writer.flush();
        } catch (Exception e) {
            log.error("Failed to export revenue CSV", e);
        }
        return baos.toByteArray();
    }

    @Override
    public byte[] exportRevenueXlsx(RevenueReportVO report) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // [Content_Types].xml
            zos.putNextEntry(new ZipEntry("[Content_Types].xml"));
            String contentTypes = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                    "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">\n" +
                    "  <Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>\n" +
                    "  <Default Extension=\"xml\" ContentType=\"application/xml\"/>\n" +
                    "  <Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>\n" +
                    "  <Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>\n" +
                    "</Types>";
            zos.write(contentTypes.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // _rels/.rels
            zos.putNextEntry(new ZipEntry("_rels/.rels"));
            String rels = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                    "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n" +
                    "  <Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>\n" +
                    "</Relationships>";
            zos.write(rels.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // xl/_rels/workbook.xml.rels
            zos.putNextEntry(new ZipEntry("xl/_rels/workbook.xml.rels"));
            String wbRels = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                    "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n" +
                    "  <Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>\n" +
                    "</Relationships>";
            zos.write(wbRels.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // xl/workbook.xml
            zos.putNextEntry(new ZipEntry("xl/workbook.xml"));
            String workbook = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                    "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">\n" +
                    "  <sheets>\n" +
                    "    <sheet name=\"Revenue Report\" sheetId=\"1\" r:id=\"rId1\"/>\n" +
                    "  </sheets>\n" +
                    "</workbook>";
            zos.write(workbook.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // xl/worksheets/sheet1.xml
            zos.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
            StringBuilder sheet = new StringBuilder();
            sheet.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
            sheet.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">\n");
            sheet.append("  <sheetData>\n");

            int rowIdx = 1;
            sheet.append(buildXmlRow(rowIdx++, "BAO CAO DOANH THU & THUE - NOVEL-PLUS"));
            sheet.append(buildXmlRow(rowIdx++, "Ky: " + report.getPeriod() + " | Tu: " + report.getStartDate() + " | Den: " + report.getEndDate()));
            sheet.append(buildXmlRow(rowIdx++)); // Empty row

            sheet.append(buildXmlRow(rowIdx++, "Period", "Gross Recharge", "VAT (10%)", "Net Recharge", "Xu Consumed", "Author Gross", "PIT Withheld", "Author Net Payout", "Platform Net Revenue"));

            for (RevenuePeriodItemVO item : report.getItems()) {
                sheet.append(buildXmlRow(rowIdx++,
                        item.getPeriodLabel(),
                        String.valueOf(item.getRechargeGrossVnd()),
                        String.valueOf(item.getRechargeVatVnd()),
                        String.valueOf(item.getRechargeNetVnd()),
                        String.valueOf(item.getXuConsumed()),
                        String.valueOf(item.getAuthorGrossEarningsVnd()),
                        String.valueOf(item.getAuthorPitWithheldVnd()),
                        String.valueOf(item.getAuthorPayoutDisbursedVnd()),
                        String.valueOf(item.getPlatformNetRevenueVnd())
                ));
            }

            sheet.append("  </sheetData>\n");
            sheet.append("</worksheet>");
            zos.write(sheet.toString().getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.finish();
        } catch (Exception e) {
            log.error("Failed to export XLSX report", e);
        }
        return baos.toByteArray();
    }

    @Override
    public byte[] exportRevenuePdf(RevenueReportVO report) {
        DecimalFormat df = new DecimalFormat("#,###");
        StringBuilder pdfBody = new StringBuilder();
        pdfBody.append("BAO CAO TONG HOP DOANH THU & THUE\n");
        pdfBody.append("Platform: Novel-Plus Financial Services\n");
        pdfBody.append(String.format("Ky: %s | Tu: %s | Den: %s\n", report.getPeriod(), report.getStartDate(), report.getEndDate()));
        pdfBody.append("--------------------------------------------------\n");
        pdfBody.append(String.format("1. Tong Gross Recharge VNPAY : %s VND\n", df.format(report.getTotalRechargeGrossVnd())));
        pdfBody.append(String.format("2. Tong VAT Thu (10%%)        : %s VND\n", df.format(report.getTotalRechargeVatVnd())));
        pdfBody.append(String.format("3. Tong Net Recharge         : %s VND\n", df.format(report.getTotalRechargeNetVnd())));
        pdfBody.append(String.format("4. Tong Xu Phat Hanh         : %s Xu\n", df.format(report.getTotalXuIssued())));
        pdfBody.append(String.format("5. Tong Xu Tieu Dung         : %s Xu\n", df.format(report.getTotalXuConsumed())));
        pdfBody.append(String.format("6. Tong Gross Tac Gia        : %s VND\n", df.format(report.getTotalAuthorGrossEarningsVnd())));
        pdfBody.append(String.format("7. Tong Khau Tru Thue TNCN   : %s VND\n", df.format(report.getTotalAuthorPitWithheldVnd())));
        pdfBody.append(String.format("8. Tong Chi Payout Net       : %s VND\n", df.format(report.getTotalAuthorPayoutDisbursedVnd())));
        pdfBody.append(String.format("9. LOI NHUAN RONG NEN TANG   : %s VND\n", df.format(report.getPlatformNetRevenueVnd())));
        pdfBody.append("--------------------------------------------------\n");
        pdfBody.append("CHI TIET THEO BANG THOI GIAN:\n");

        for (RevenuePeriodItemVO item : report.getItems()) {
            pdfBody.append(String.format("[%s] Gross:%s | VAT:%s | Net:%s | TacGiaNet:%s | NenTangNet:%s\n",
                    item.getPeriodLabel(),
                    df.format(item.getRechargeGrossVnd()),
                    df.format(item.getRechargeVatVnd()),
                    df.format(item.getRechargeNetVnd()),
                    df.format(item.getAuthorPayoutDisbursedVnd()),
                    df.format(item.getPlatformNetRevenueVnd())
            ));
        }

        return generatePdfDocument("BAO CAO TAI CHINH NOVEL-PLUS", pdfBody.toString());
    }

    @Override
    public byte[] exportRevenueJson(RevenueReportVO report) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(report);
        } catch (Exception e) {
            log.error("Failed to export JSON revenue report", e);
            return "{}".getBytes(StandardCharsets.UTF_8);
        }
    }

    @Override
    public Map<String, Object> getPitSummaryReport(String startDate, String endDate) {
        List<Map<String, Object>> payouts = revenueReportDao.selectPayouts(startDate, endDate);

        long totalGross = 0;
        long totalPit = 0;
        long totalNet = 0;
        int count = payouts.size();

        for (Map<String, Object> p : payouts) {
            long gross = p.get("grossAmountVnd") != null ? ((Number) p.get("grossAmountVnd")).longValue() : 0;
            long tax = p.get("withheldTaxVnd") != null ? ((Number) p.get("withheldTaxVnd")).longValue() : 0;
            long net = p.get("netAmountVnd") != null ? ((Number) p.get("netAmountVnd")).longValue() : 0;

            totalGross += gross;
            totalPit += tax;
            totalNet += net;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reportTitle", "Bao cao khau tru thue TNCN thu nhap tac gia");
        result.put("circularReference", "Thong tu 111/2013/TT-BTC & Thong tu 92/2015/TT-BTC");
        result.put("startDate", startDate);
        result.put("endDate", endDate);
        result.put("totalPayoutsCount", count);
        result.put("totalGrossAmountVnd", totalGross);
        result.put("totalWithheldPitVnd", totalPit);
        result.put("totalNetDisbursedVnd", totalNet);
        result.put("defaultTaxRate", "10%");
        result.put("exemptionThresholdVnd", PitTaxCalculatorService.DEFAULT_EXEMPTION_THRESHOLD_VND);
        return result;
    }

    @Override
    public Map<String, Object> getVatSummaryReport(String startDate, String endDate) {
        List<Map<String, Object>> recharges = revenueReportDao.selectRecharges(startDate, endDate);

        long totalGross = 0;
        long totalVat = 0;
        long totalNet = 0;
        int count = recharges.size();

        for (Map<String, Object> r : recharges) {
            long gross = r.get("totalAmount") != null ? ((Number) r.get("totalAmount")).longValue() : 0;
            VatTaxResult vat = vatCalculatorService.calculateVat(gross);

            totalGross += gross;
            totalVat += vat.getVatAmountVnd();
            totalNet += vat.getNetAmountVnd();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reportTitle", "Bao cao doanh thu va thue GTGT nap tien VNPAY");
        result.put("startDate", startDate);
        result.put("endDate", endDate);
        result.put("totalRechargeCount", count);
        result.put("totalGrossRechargeVnd", totalGross);
        result.put("totalVatAmountVnd", totalVat);
        result.put("totalNetRevenueVnd", totalNet);
        result.put("vatRate", "10%");
        return result;
    }

    private String getPeriodLabel(Date date, String periodType) {
        if (date == null) return "UNKNOWN";
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);

        if ("MONTHLY".equalsIgnoreCase(periodType)) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM");
            return sdf.format(date);
        } else if ("QUARTERLY".equalsIgnoreCase(periodType)) {
            int year = cal.get(Calendar.YEAR);
            int quarter = (cal.get(Calendar.MONTH) / 3) + 1;
            return year + "-Q" + quarter;
        } else if ("ANNUAL".equalsIgnoreCase(periodType)) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy");
            return sdf.format(date);
        } else {
            // DAILY default
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            return sdf.format(date);
        }
    }

    private String buildXmlRow(int rowIdx, String... values) {
        StringBuilder sb = new StringBuilder();
        sb.append("    <row r=\"").append(rowIdx).append("\">\n");
        for (int i = 0; i < values.length; i++) {
            String colLetter = getColumnLetter(i + 1);
            String val = values[i] != null ? values[i] : "";
            sb.append("      <c r=\"").append(colLetter).append(rowIdx).append("\" t=\"inlineStr\"><is><t>")
              .append(escapeXml(val)).append("</t></is></c>\n");
        }
        sb.append("    </row>\n");
        return sb.toString();
    }

    private String getColumnLetter(int colNum) {
        StringBuilder col = new StringBuilder();
        while (colNum > 0) {
            int rem = (colNum - 1) % 26;
            col.insert(0, (char) ('A' + rem));
            colNum = (colNum - 1) / 26;
        }
        return col.toString();
    }

    private String escapeXml(String str) {
        if (str == null) return "";
        return str.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private byte[] generatePdfDocument(String title, String bodyText) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            StringBuilder streamContent = new StringBuilder();
            streamContent.append("BT\n/F1 14 Tf\n50 750 Td\n(").append(escapePdfText(title)).append(") Tj\n");
            streamContent.append("/F1 10 Tf\n0 -25 Td\n");

            for (String line : bodyText.split("\n")) {
                streamContent.append("(").append(escapePdfText(line)).append(") Tj\n0 -15 Td\n");
            }
            streamContent.append("ET\n");

            byte[] streamBytes = streamContent.toString().getBytes(StandardCharsets.ISO_8859_1);
            StringBuilder pdf = new StringBuilder();
            pdf.append("%PDF-1.4\n");
            int o1 = pdf.length();
            pdf.append("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");
            int o2 = pdf.length();
            pdf.append("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n");
            int o3 = pdf.length();
            pdf.append("3 0 obj\n<< /Type /Page /Parent 2 0 R /Resources << /Font << /F1 4 0 R >> >> /MediaBox [0 0 612 792] /Contents 5 0 R >>\nendobj\n");
            int o4 = pdf.length();
            pdf.append("4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Courier >>\nendobj\n");
            int o5 = pdf.length();
            pdf.append("5 0 obj\n<< /Length ").append(streamBytes.length).append(" >>\nstream\n");

            out.write(pdf.toString().getBytes(StandardCharsets.ISO_8859_1));
            out.write(streamBytes);

            StringBuilder pdfTrailer = new StringBuilder();
            pdfTrailer.append("\nendstream\nendobj\n");
            int xrefOffset = out.size() + pdfTrailer.length();
            pdfTrailer.append("xref\n0 6\n0000000000 65535 f \n");
            pdfTrailer.append(String.format("%010d 00000 n \n", o1));
            pdfTrailer.append(String.format("%010d 00000 n \n", o2));
            pdfTrailer.append(String.format("%010d 00000 n \n", o3));
            pdfTrailer.append(String.format("%010d 00000 n \n", o4));
            pdfTrailer.append(String.format("%010d 00000 n \n", o5));
            pdfTrailer.append("trailer\n<< /Size 6 /Root 1 0 R >>\n");
            pdfTrailer.append("startxref\n").append(xrefOffset).append("\n%%EOF\n");

            out.write(pdfTrailer.toString().getBytes(StandardCharsets.ISO_8859_1));
            return out.toByteArray();
        } catch (Exception e) {
            return ("%PDF-1.4 Error: " + e.getMessage()).getBytes(StandardCharsets.UTF_8);
        }
    }

    private String escapePdfText(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }
}
