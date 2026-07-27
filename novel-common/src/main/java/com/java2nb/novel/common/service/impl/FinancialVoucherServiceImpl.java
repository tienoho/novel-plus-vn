package com.java2nb.novel.common.service.impl;

import com.java2nb.novel.common.dao.FinancialVoucherDao;
import com.java2nb.novel.common.entity.FinancialVoucherDO;
import com.java2nb.novel.common.service.FinancialVoucherService;
import com.java2nb.novel.common.tax.PitTaxCalculatorService;
import com.java2nb.novel.common.tax.PitTaxResult;
import com.java2nb.novel.common.tax.VatTaxCalculatorService;
import com.java2nb.novel.common.tax.VatTaxResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FinancialVoucherServiceImpl implements FinancialVoucherService {

    private final FinancialVoucherDao voucherDao;
    private final VatTaxCalculatorService vatCalculatorService;
    private final PitTaxCalculatorService pitCalculatorService;

    @Value("${financial.voucher.issuance-enabled:false}")
    private boolean issuanceEnabled;

    @Value("${financial.voucher.platform-legal-name:}")
    private String platformLegalName;

    @Value("${financial.voucher.platform-tax-code:}")
    private String platformTaxCode;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public FinancialVoucherDO createRechargeReceipt(String orderNo, String payerName, String payerTaxCode, long grossAmountVnd) {
        FinancialVoucherDO existing = voucherDao.selectByReference("ORDER_PAY", orderNo);
        if (existing != null) {
            return existing;
        }
        requireIssuanceConfigured();

        VatTaxResult vatResult = vatCalculatorService.calculateVat(grossAmountVnd);
        String voucherNo = generateVoucherNo("INV");

        FinancialVoucherDO voucher = FinancialVoucherDO.builder()
                .voucherNo(voucherNo)
                .voucherType("RECHARGE_RECEIPT")
                .referenceType("ORDER_PAY")
                .referenceId(orderNo)
                .payerName(StringUtils.defaultIfBlank(payerName, "Khach hang nap Xu"))
                .payerTaxCode(StringUtils.defaultIfBlank(payerTaxCode, "N/A"))
                .payeeName(platformLegalName.trim())
                .payeeTaxCode(platformTaxCode.trim())
                .grossAmountVnd(grossAmountVnd)
                .taxAmountVnd(vatResult.getVatAmountVnd())
                .netAmountVnd(vatResult.getNetAmountVnd())
                .currency("VND")
                .status("ISSUED")
                .issuedAt(new Date())
                .build();

        voucherDao.insert(voucher);
        log.info("Issued recharge receipt: {}", voucherNo);
        return voucher;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public FinancialVoucherDO createPayoutVoucher(long withdrawalId, String payeeName, String payeeTaxCode, long grossAmountVnd, long withheldTaxVnd) {
        String refId = String.valueOf(withdrawalId);
        FinancialVoucherDO existing = voucherDao.selectByReference("AUTHOR_WITHDRAWAL_REQUEST", refId);
        if (existing != null) {
            return existing;
        }
        requireIssuanceConfigured();

        long actualTax = withheldTaxVnd;
        if (actualTax <= 0 && grossAmountVnd >= PitTaxCalculatorService.DEFAULT_EXEMPTION_THRESHOLD_VND) {
            PitTaxResult pitResult = pitCalculatorService.calculatePit(grossAmountVnd);
            actualTax = pitResult.getWithheldTaxVnd();
        }

        long netAmount = grossAmountVnd - actualTax;
        String voucherNo = generateVoucherNo("VOUCHER");

        FinancialVoucherDO voucher = FinancialVoucherDO.builder()
                .voucherNo(voucherNo)
                .voucherType("AUTHOR_PAYOUT_VOUCHER")
                .referenceType("AUTHOR_WITHDRAWAL_REQUEST")
                .referenceId(refId)
                .payerName(platformLegalName.trim())
                .payerTaxCode(platformTaxCode.trim())
                .payeeName(StringUtils.defaultIfBlank(payeeName, "Tac gia nhan payout"))
                .payeeTaxCode(StringUtils.defaultIfBlank(payeeTaxCode, "N/A"))
                .grossAmountVnd(grossAmountVnd)
                .taxAmountVnd(actualTax)
                .netAmountVnd(netAmount)
                .currency("VND")
                .status("ISSUED")
                .issuedAt(new Date())
                .build();

        voucherDao.insert(voucher);
        log.info("Issued payout voucher: {}", voucherNo);
        return voucher;
    }

    @Override
    public FinancialVoucherDO getByVoucherNo(String voucherNo) {
        return voucherDao.selectByVoucherNo(voucherNo);
    }

    @Override
    public FinancialVoucherDO getByReference(String referenceType, String referenceId) {
        return voucherDao.selectByReference(referenceType, referenceId);
    }

    @Override
    public List<FinancialVoucherDO> listVouchers(Map<String, Object> params) {
        return voucherDao.selectList(params);
    }

    @Override
    public int countVouchers(Map<String, Object> params) {
        return voucherDao.countList(params);
    }

    @Override
    public byte[] exportVoucherPdf(String voucherNo) {
        FinancialVoucherDO voucher = voucherDao.selectByVoucherNo(voucherNo);
        if (voucher == null) {
            throw new IllegalArgumentException("Khong tim thay chung tu: " + voucherNo);
        }

        DecimalFormat df = new DecimalFormat("#,###");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        String title = "RECHARGE_RECEIPT".equals(voucher.getVoucherType()) ?
                "BIEN NHAN NAP TIEN (RECHARGE RECEIPT)" : "PHIEU CHI TAI CHINH (PAYOUT VOUCHER)";

        String contentText = String.format(
                "BT / Title: %s\n" +
                "So chung tu / Voucher No: %s\n" +
                "Ngay phat hanh / Date: %s\n" +
                "Trang thai / Status: %s\n" +
                "----------------------------------------\n" +
                "Nguoi tra / Payer: %s (MST: %s)\n" +
                "Nguoi nhan / Payee: %s (MST: %s)\n" +
                "Loai tham chiếu / Ref Type: %s (ID: %s)\n" +
                "----------------------------------------\n" +
                "Tong tien gop / Gross Amount: %s VND\n" +
                "Thue khau tru / Tax Amount: %s VND (%s)\n" +
                "Tien thuc nhan / Net Amount: %s VND\n" +
                "----------------------------------------\n" +
                "Xac thuc / Checksum: %s\n",
                title,
                voucher.getVoucherNo(),
                voucher.getIssuedAt() != null ? sdf.format(voucher.getIssuedAt()) : sdf.format(new Date()),
                voucher.getStatus(),
                voucher.getPayerName(), voucher.getPayerTaxCode(),
                voucher.getPayeeName(), voucher.getPayeeTaxCode(),
                voucher.getReferenceType(), voucher.getReferenceId(),
                df.format(voucher.getGrossAmountVnd()),
                df.format(voucher.getTaxAmountVnd()),
                "RECHARGE_RECEIPT".equals(voucher.getVoucherType()) ? "VAT 10%" : "PIT 10%",
                df.format(voucher.getNetAmountVnd()),
                computeSha256(voucher.getVoucherNo() + "|" + voucher.getGrossAmountVnd() + "|" + voucher.getNetAmountVnd())
        );

        return generatePdfFromText(title, contentText);
    }

    @Override
    public byte[] exportVouchersCsv(List<FinancialVoucherDO> list) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8))) {
            // Write UTF-8 BOM for Excel compatibility
            baos.write(0xEF);
            baos.write(0xBB);
            baos.write(0xBF);

            writer.println("Voucher No,Voucher Type,Ref Type,Ref ID,Payer Name,Payer Tax Code,Payee Name,Payee Tax Code,Gross Amount (VND),Tax Amount (VND),Net Amount (VND),Status,Issued At");

            DecimalFormat df = new DecimalFormat("#");
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

            for (FinancialVoucherDO v : list) {
                writer.printf("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",%d,%d,%d,\"%s\",\"%s\"\n",
                        escapeCsv(v.getVoucherNo()),
                        escapeCsv(v.getVoucherType()),
                        escapeCsv(v.getReferenceType()),
                        escapeCsv(v.getReferenceId()),
                        escapeCsv(v.getPayerName()),
                        escapeCsv(v.getPayerTaxCode()),
                        escapeCsv(v.getPayeeName()),
                        escapeCsv(v.getPayeeTaxCode()),
                        v.getGrossAmountVnd() != null ? v.getGrossAmountVnd() : 0,
                        v.getTaxAmountVnd() != null ? v.getTaxAmountVnd() : 0,
                        v.getNetAmountVnd() != null ? v.getNetAmountVnd() : 0,
                        escapeCsv(v.getStatus()),
                        v.getIssuedAt() != null ? sdf.format(v.getIssuedAt()) : ""
                );
            }
            writer.flush();
        } catch (Exception e) {
            log.error("Failed to generate CSV export", e);
        }
        return baos.toByteArray();
    }

    @Override
    public byte[] exportVouchersJson(List<FinancialVoucherDO> list) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(list);
        } catch (Exception e) {
            log.error("Failed to export JSON vouchers list", e);
            return "[]".getBytes(StandardCharsets.UTF_8);
        }
    }

    @Override
    public byte[] exportVoucherJson(FinancialVoucherDO voucher) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(voucher);
        } catch (Exception e) {
            log.error("Failed to export JSON voucher", e);
            return "{}".getBytes(StandardCharsets.UTF_8);
        }
    }

    private String generateVoucherNo(String prefix) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        String dateStr = sdf.format(new Date());
        String entropy = UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
        return prefix + "-" + dateStr + "-" + entropy;
    }

    private void requireIssuanceConfigured() {
        if (!issuanceEnabled) {
            throw new IllegalStateException("Phát hành chứng từ tài chính đang bị tắt");
        }
        if (StringUtils.isBlank(platformLegalName)) {
            throw new IllegalStateException("Thiếu tên pháp nhân phát hành chứng từ");
        }
        String normalizedTaxCode = StringUtils.trimToEmpty(platformTaxCode);
        if (!normalizedTaxCode.matches("\\d{10}(-\\d{3})?")) {
            throw new IllegalStateException("Mã số thuế pháp nhân không hợp lệ");
        }
    }

    private String escapeCsv(String str) {
        if (str == null) return "";
        return str.replace("\"", "\"\"");
    }

    private String computeSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return "HASH_ERROR";
        }
    }

    /**
     * Helper to construct a binary PDF 1.4 file containing printable document text.
     */
    private byte[] generatePdfFromText(String title, String bodyText) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            // Prepare text commands for PDF stream
            StringBuilder streamContent = new StringBuilder();
            streamContent.append("BT\n");
            streamContent.append("/F1 14 Tf\n");
            streamContent.append("50 750 Td\n");
            streamContent.append("(").append(escapePdfText(title)).append(") Tj\n");
            streamContent.append("/F1 10 Tf\n");
            streamContent.append("0 -25 Td\n");

            String[] lines = bodyText.split("\n");
            for (String line : lines) {
                streamContent.append("(").append(escapePdfText(line)).append(") Tj\n");
                streamContent.append("0 -15 Td\n");
            }
            streamContent.append("ET\n");

            byte[] streamBytes = streamContent.toString().getBytes(StandardCharsets.ISO_8859_1);

            // Construct PDF objects
            StringBuilder pdf = new StringBuilder();
            pdf.append("%PDF-1.4\n");

            // Object 1: Catalog
            int obj1Offset = pdf.length();
            pdf.append("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");

            // Object 2: Pages
            int obj2Offset = pdf.length();
            pdf.append("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n");

            // Object 3: Page
            int obj3Offset = pdf.length();
            pdf.append("3 0 obj\n<< /Type /Page /Parent 2 0 R /Resources << /Font << /F1 4 0 R >> >> /MediaBox [0 0 612 792] /Contents 5 0 R >>\nendobj\n");

            // Object 4: Font
            int obj4Offset = pdf.length();
            pdf.append("4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Courier >>\nendobj\n");

            // Object 5: Content Stream Header
            int obj5Offset = pdf.length();
            pdf.append("5 0 obj\n<< /Length ").append(streamBytes.length).append(" >>\nstream\n");
            out.write(pdf.toString().getBytes(StandardCharsets.ISO_8859_1));
            out.write(streamBytes);

            StringBuilder pdfTrailer = new StringBuilder();
            pdfTrailer.append("\nendstream\nendobj\n");

            // Xref Table
            int xrefOffset = out.size() + pdfTrailer.length();
            pdfTrailer.append("xref\n");
            pdfTrailer.append("0 6\n");
            pdfTrailer.append("0000000000 65535 f \n");
            pdfTrailer.append(String.format("%010d 00000 n \n", obj1Offset));
            pdfTrailer.append(String.format("%010d 00000 n \n", obj2Offset));
            pdfTrailer.append(String.format("%010d 00000 n \n", obj3Offset));
            pdfTrailer.append(String.format("%010d 00000 n \n", obj4Offset));
            pdfTrailer.append(String.format("%010d 00000 n \n", obj5Offset));
            pdfTrailer.append("trailer\n<< /Size 6 /Root 1 0 R >>\n");
            pdfTrailer.append("startxref\n").append(xrefOffset).append("\n%%EOF\n");

            out.write(pdfTrailer.toString().getBytes(StandardCharsets.ISO_8859_1));
            return out.toByteArray();
        } catch (Exception e) {
            log.error("PDF generation error", e);
            return ("%PDF-1.4 Error generating PDF: " + e.getMessage()).getBytes(StandardCharsets.UTF_8);
        }
    }

    private String escapePdfText(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace("(", "\\(")
                   .replace(")", "\\)");
    }
}
