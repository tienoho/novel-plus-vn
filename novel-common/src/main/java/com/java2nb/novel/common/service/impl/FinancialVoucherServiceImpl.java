package com.java2nb.novel.common.service.impl;

import com.java2nb.novel.common.dao.FinancialVoucherDao;
import com.java2nb.novel.common.entity.FinancialVoucherDO;
import com.java2nb.novel.common.service.FinancialVoucherService;
import com.java2nb.novel.common.tax.PitTaxCalculatorService;
import com.java2nb.novel.common.tax.PitTaxResult;
import com.java2nb.novel.common.tax.VatTaxCalculatorService;
import com.java2nb.novel.common.tax.VatTaxResult;
import com.java2nb.novel.core.enums.ResponseStatus;
import com.java2nb.novel.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FinancialVoucherServiceImpl implements FinancialVoucherService {

    private static final String REGULAR_FONT = "/fonts/ttf/OpenSans/OpenSans-Regular.ttf";
    private static final String BOLD_FONT = "/fonts/ttf/OpenSans/OpenSans-Bold.ttf";
    private static final int PDF_BODY_LINES_PER_PAGE = 40;

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
    public FinancialVoucherDO createRechargeReceipt(String orderNo, String payerName, String payerTaxCode,
            long grossAmountVnd) {
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
                .payerName(StringUtils.defaultIfBlank(payerName, "Khách hàng nạp Xu"))
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
    public FinancialVoucherDO createPayoutVoucher(long withdrawalId, String payeeName, String payeeTaxCode,
            long grossAmountVnd, long withheldTaxVnd) {
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
                .payeeName(StringUtils.defaultIfBlank(payeeName, "Tác giả nhận thu nhập"))
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
    public FinancialVoucherDO getAuthorVoucher(String voucherNo, long authorId) {
        requireAuthorIdentity(authorId);
        return voucherDao.selectAuthorVoucherByNo(voucherNo, authorId);
    }

    @Override
    public List<FinancialVoucherDO> listAuthorVouchers(long authorId) {
        requireAuthorIdentity(authorId);
        return voucherDao.selectAuthorVouchers(authorId);
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
            throw new IllegalArgumentException("Không tìm thấy chứng từ: " + voucherNo);
        }

        return exportVoucherPdf(voucher);
    }

    @Override
    public byte[] exportAuthorVoucherPdf(String voucherNo, long authorId) {
        FinancialVoucherDO voucher = getAuthorVoucher(voucherNo, authorId);
        if (voucher == null) {
            throw new BusinessException(ResponseStatus.AUTHOR_VOUCHER_NOT_FOUND);
        }

        return exportVoucherPdf(voucher);
    }

    private byte[] exportVoucherPdf(FinancialVoucherDO voucher) {
        NumberFormat numberFormat = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN"));
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.forLanguageTag("vi-VN"));
        dateFormat.setTimeZone(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));

        String title = "RECHARGE_RECEIPT".equals(voucher.getVoucherType()) ? "BIÊN NHẬN NẠP XU"
                : "PHIẾU CHI THU NHẬP TÁC GIẢ";
        String taxLabel = "RECHARGE_RECEIPT".equals(voucher.getVoucherType())
                ? "Thuế GTGT"
                : "Thuế TNCN đã khấu trừ";

        String contentText = String.format(
                "Số chứng từ: %s\n" +
                        "Ngày phát hành: %s\n" +
                        "Trạng thái: %s\n" +
                        "----------------------------------------\n" +
                        "Người trả: %s (MST: %s)\n" +
                        "Người nhận: %s (MST: %s)\n" +
                        "Tham chiếu: %s (ID: %s)\n" +
                        "----------------------------------------\n" +
                        "Tổng tiền: %s VND\n" +
                        "%s: %s VND\n" +
                        "Thực nhận: %s VND\n" +
                        "----------------------------------------\n" +
                        "Mã kiểm tra: %s\n",
                voucher.getVoucherNo(),
                voucher.getIssuedAt() != null ? dateFormat.format(voucher.getIssuedAt())
                        : dateFormat.format(new Date()),
                voucher.getStatus(),
                voucher.getPayerName(), voucher.getPayerTaxCode(),
                voucher.getPayeeName(), voucher.getPayeeTaxCode(),
                voucher.getReferenceType(), voucher.getReferenceId(),
                numberFormat.format(voucher.getGrossAmountVnd()),
                taxLabel, numberFormat.format(voucher.getTaxAmountVnd()),
                numberFormat.format(voucher.getNetAmountVnd()),
                computeSha256(
                        voucher.getVoucherNo() + "|" + voucher.getGrossAmountVnd() + "|" + voucher.getNetAmountVnd()));

        return generatePdfFromText(title, contentText);
    }

    private void requireAuthorIdentity(long authorId) {
        if (authorId <= 0) {
            throw new IllegalArgumentException("Tác giả không hợp lệ");
        }
    }

    @Override
    public byte[] exportVouchersCsv(List<FinancialVoucherDO> list) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8))) {
            // Write UTF-8 BOM for Excel compatibility
            baos.write(0xEF);
            baos.write(0xBB);
            baos.write(0xBF);

            writer.println(
                    "Voucher No,Voucher Type,Ref Type,Ref ID,Payer Name,Payer Tax Code,Payee Name,Payee Tax Code,Gross Amount (VND),Tax Amount (VND),Net Amount (VND),Status,Issued At");

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            sdf.setTimeZone(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));

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
                        v.getIssuedAt() != null ? sdf.format(v.getIssuedAt()) : "");
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
        if (str == null)
            return "";
        return str.replace("\"", "\"\"");
    }

    private String computeSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1)
                    hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return "HASH_ERROR";
        }
    }

    private byte[] generatePdfFromText(String title, String bodyText) {
        try (PDDocument document = new PDDocument();
                InputStream regularInput = requireFont(REGULAR_FONT);
                InputStream boldInput = requireFont(BOLD_FONT);
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDFont regular = PDType0Font.load(document, regularInput, true);
            PDFont bold = PDType0Font.load(document, boldInput, true);
            List<String> lines = new ArrayList<>();
            for (String rawLine : bodyText.split("\\R")) {
                lines.addAll(wrapLine(regular, rawLine, 10, 495));
            }
            if (lines.isEmpty()) {
                lines.add("");
            }

            for (int offset = 0; offset < lines.size(); offset += PDF_BODY_LINES_PER_PAGE) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.newLineAtOffset(50, 790);
                    content.setFont(bold, 14);
                    content.showText(offset == 0 ? title : title + " (tiếp)");
                    content.setLeading(16);
                    content.newLine();
                    content.newLine();
                    content.setFont(regular, 10);
                    int end = Math.min(offset + PDF_BODY_LINES_PER_PAGE, lines.size());
                    for (int index = offset; index < end; index++) {
                        content.showText(lines.get(index));
                        content.newLine();
                    }
                    content.endText();
                }
            }

            PDDocumentInformation information = new PDDocumentInformation();
            information.setTitle(title);
            information.setProducer("Khởi Thư");
            document.setDocumentInformation(information);
            document.save(output);
            return output.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Không thể tạo PDF chứng từ", e);
        }
    }

    private InputStream requireFont(String resourcePath) {
        InputStream input = FinancialVoucherServiceImpl.class.getResourceAsStream(resourcePath);
        if (input == null) {
            throw new IllegalStateException("Thiếu font PDF: " + resourcePath);
        }
        return input;
    }

    private List<String> wrapLine(PDFont font, String value, float fontSize, float maxWidth)
            throws Exception {
        String text = value == null ? "" : value.strip();
        if (text.isEmpty()) {
            return List.of("");
        }
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split("\\s+")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            float width = font.getStringWidth(candidate) / 1000f * fontSize;
            if (width <= maxWidth || current.isEmpty()) {
                current.setLength(0);
                current.append(candidate);
            } else {
                lines.add(current.toString());
                current.setLength(0);
                current.append(word);
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }
}
