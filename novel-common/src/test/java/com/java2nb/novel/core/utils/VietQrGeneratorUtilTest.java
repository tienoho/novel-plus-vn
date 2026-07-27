package com.java2nb.novel.core.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VietQrGeneratorUtilTest {

    @Test
    void testComputeCrc16CcittFalseStandardString() {
        // Standard check for string "123456789"
        String crc = VietQrGeneratorUtil.computeCrc16CcittFalse("123456789");
        assertThat(crc).isEqualTo("29B1");
    }

    @Test
    void testGenerateEmvCoQrStructure() {
        String bankBin = "970422";
        String accountNo = "1234567890";
        long amountVnd = 50000;
        String paymentRef = "1002003004";

        String qrCode = VietQrGeneratorUtil.generateEmvCoQr(bankBin, accountNo, amountVnd, paymentRef);

        assertThat(qrCode).startsWith("000201010212"); // Payload format & dynamic initiation
        assertThat(qrCode).contains("A000000727"); // VietQR GUID
        assertThat(qrCode).contains("970422"); // MBBank BIN
        assertThat(qrCode).contains("1234567890"); // Account No
        assertThat(qrCode).contains("5303704"); // VND Currency
        assertThat(qrCode).contains("540550000"); // Amount 50000
        assertThat(qrCode).contains("5802VN"); // Country code
        assertThat(qrCode).contains("6304"); // CRC tag

        // Verify that the generated CRC matches the computed CRC of the payload prefix
        String payloadWithoutCrc = qrCode.substring(0, qrCode.length() - 4);
        String expectedCrc = VietQrGeneratorUtil.computeCrc16CcittFalse(payloadWithoutCrc);
        String actualCrc = qrCode.substring(qrCode.length() - 4);

        assertThat(actualCrc).isEqualTo(expectedCrc);
    }

    @Test
    void testGenerateQuickLinkUrl() {
        String bankBin = "970422";
        String accountNo = "1234567890";
        long amountVnd = 100000;
        String paymentRef = "1002003004";
        String accountName = "NOVEL PLUS";

        String url = VietQrGeneratorUtil.generateQuickLinkUrl(bankBin, accountNo, amountVnd, paymentRef, accountName);

        assertThat(url).startsWith("https://img.vietqr.io/image/970422-1234567890-compact2.png");
        assertThat(url).contains("amount=100000");
        assertThat(url).contains("addInfo=1002003004");
        assertThat(url).contains("accountName=NOVEL+PLUS");
    }
}
