package com.java2nb.novel.core.utils;

import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.util.Objects;

/**
 * Tiện ích sinh chuỗi EMVCo VietQR và URL VietQR Quick Link
 */
public class VietQrGeneratorUtil {

    private static final String PAYLOAD_FORMAT_INDICATOR = "000201";
    private static final String POINT_OF_INITIATION_STATIC = "010211";
    private static final String POINT_OF_INITIATION_DYNAMIC = "010212";
    private static final String GUID_VIETQR = "A000000727";
    private static final String SERVICE_CODE_ACCOUNT = "QRIBFTTA";
    private static final String CURRENCY_VND = "704";
    private static final String COUNTRY_VN = "VN";

    private VietQrGeneratorUtil() {
    }

    /**
     * Sinh chuỗi VietQR chuẩn EMVCo
     *
     * @param bankBin       Mã BIN ngân hàng (ví dụ: 970422 cho MBBank, 970436 cho Vietcombank)
     * @param accountNo     Số tài khoản thụ hưởng
     * @param amountVnd     Số tiền giao dịch (VND)
     * @param paymentRef    Nội dung thanh toán (ví dụ: mã đơn outTradeNo)
     * @return Chuỗi EMVCo QR code hoàn chỉnh kèm CRC16
     */
    public static String generateEmvCoQr(String bankBin, String accountNo, long amountVnd, String paymentRef) {
        Objects.requireNonNull(bankBin, "Mã BIN ngân hàng không được để trống");
        Objects.requireNonNull(accountNo, "Số tài khoản không được để trống");

        StringBuilder payload = new StringBuilder();
        payload.append(PAYLOAD_FORMAT_INDICATOR);
        payload.append(amountVnd > 0 ? POINT_OF_INITIATION_DYNAMIC : POINT_OF_INITIATION_STATIC);

        // Tag 38: Merchant Account Information
        StringBuilder bankInfo = new StringBuilder();
        bankInfo.append(formatTlv("00", bankBin));
        bankInfo.append(formatTlv("01", accountNo));

        StringBuilder tag38Content = new StringBuilder();
        tag38Content.append(formatTlv("00", GUID_VIETQR));
        tag38Content.append(formatTlv("01", bankInfo.toString()));
        tag38Content.append(formatTlv("02", SERVICE_CODE_ACCOUNT));

        payload.append(formatTlv("38", tag38Content.toString()));

        // Tag 53: Transaction Currency (VND = 704)
        payload.append(formatTlv("53", CURRENCY_VND));

        // Tag 54: Transaction Amount
        if (amountVnd > 0) {
            payload.append(formatTlv("54", String.valueOf(amountVnd)));
        }

        // Tag 58: Country Code (VN)
        payload.append(formatTlv("58", COUNTRY_VN));

        // Tag 62: Additional Data Field Template (Purpose / Reference)
        if (paymentRef != null && !paymentRef.trim().isEmpty()) {
            String tag08 = formatTlv("08", paymentRef.trim());
            payload.append(formatTlv("62", tag08));
        }

        // Tag 63: CRC16
        payload.append("6304");

        String crcHex = computeCrc16CcittFalse(payload.toString());
        payload.append(crcHex);

        return payload.toString();
    }

    /**
     * Sinh URL ảnh Quick Link VietQR làm phương án dự phòng hiển thị QR code
     */
    public static String generateQuickLinkUrl(String bankBin, String accountNo, long amountVnd, String paymentRef, String accountName) {
        Objects.requireNonNull(bankBin, "Mã BIN ngân hàng không được để trống");
        Objects.requireNonNull(accountNo, "Số tài khoản không được để trống");

        StringBuilder url = new StringBuilder("https://img.vietqr.io/image/");
        url.append(bankBin).append("-").append(accountNo).append("-compact2.png");

        StringBuilder params = new StringBuilder();
        if (amountVnd > 0) {
            params.append("amount=").append(amountVnd);
        }
        if (paymentRef != null && !paymentRef.trim().isEmpty()) {
            if (params.length() > 0) params.append("&");
            params.append("addInfo=").append(urlEncode(paymentRef.trim()));
        }
        if (accountName != null && !accountName.trim().isEmpty()) {
            if (params.length() > 0) params.append("&");
            params.append("accountName=").append(urlEncode(accountName.trim()));
        }

        if (params.length() > 0) {
            url.append("?").append(params);
        }
        return url.toString();
    }

    /**
     * Tính mã kiểm tra CRC16 CCITT-FALSE (Polynomial 0x1021, Initial 0xFFFF)
     */
    public static String computeCrc16CcittFalse(String str) {
        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        int crc = 0xFFFF;
        int polynomial = 0x1021;

        for (byte b : bytes) {
            for (int i = 0; i < 8; i++) {
                boolean bit = ((b >> (7 - i)) & 1) == 1;
                boolean c15 = ((crc >> 15) & 1) == 1;
                crc <<= 1;
                if (c15 ^ bit) {
                    crc ^= polynomial;
                }
            }
        }
        crc &= 0xFFFF;
        return String.format("%04X", crc);
    }

    private static String formatTlv(String tag, String value) {
        int length = value.getBytes(StandardCharsets.UTF_8).length;
        return tag + String.format("%02d", length) + value;
    }

    private static String urlEncode(String text) {
        try {
            return URLEncoder.encode(text, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return text;
        }
    }
}
