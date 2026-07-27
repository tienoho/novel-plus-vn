package com.java2nb.novel.core.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Challenge 2: VietQR EMVCo CRC16 Generation & Payload Formatting")
class VietQrGeneratorChallengeTest {

    @Test
    @DisplayName("CRC16 calculation for standard ASCII string '123456789' produces '29B1'")
    void testComputeCrc16CcittFalseStandardAscii() {
        String crc = VietQrGeneratorUtil.computeCrc16CcittFalse("123456789");
        assertThat(crc).isEqualTo("29B1");
    }

    @Test
    @DisplayName("EMVCo QR structure adheres to specification for standard inputs")
    void testGenerateEmvCoQrStructure() {
        String bankBin = "970422";
        String accountNo = "1234567890";
        long amountVnd = 50000;
        String paymentRef = "1002003004";

        String qrCode = VietQrGeneratorUtil.generateEmvCoQr(bankBin, accountNo, amountVnd, paymentRef);

        assertThat(qrCode).startsWith("000201010212");
        assertThat(qrCode).contains("A000000727");
        assertThat(qrCode).contains("970422");
        assertThat(qrCode).contains("1234567890");
        assertThat(qrCode).contains("5303704");
        assertThat(qrCode).contains("540550000");
        assertThat(qrCode).contains("5802VN");
        assertThat(qrCode).contains("6304");

        String payloadWithoutCrc = qrCode.substring(0, qrCode.length() - 4);
        String expectedCrc = VietQrGeneratorUtil.computeCrc16CcittFalse(payloadWithoutCrc);
        String actualCrc = qrCode.substring(qrCode.length() - 4);

        assertThat(actualCrc).isEqualTo(expectedCrc);
    }

    @Test
    @DisplayName("Demonstrate flaw: computeCrc16CcittFalse uses US_ASCII instead of UTF_8 for non-ASCII characters")
    void testVietnameseDiacriticalMarksInPaymentRefCausesCrcMismatch() {
        String bankBin = "970422";
        String accountNo = "1234567890";
        long amountVnd = 10000;
        // Non-ASCII Vietnamese text in payment reference
        String paymentRef = "Nạp xu Novel";

        String qrCode = VietQrGeneratorUtil.generateEmvCoQr(bankBin, accountNo, amountVnd, paymentRef);

        String payloadWithoutCrc = qrCode.substring(0, qrCode.length() - 4);
        String generatedCrc = qrCode.substring(qrCode.length() - 4);

        // Compute true CRC16 over actual UTF-8 bytes of payload string
        byte[] utf8Bytes = payloadWithoutCrc.getBytes(StandardCharsets.UTF_8);
        int crc = 0xFFFF;
        int polynomial = 0x1021;
        for (byte b : utf8Bytes) {
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
        String trueUtf8Crc = String.format("%04X", crc);

        // Standard US_ASCII conversion replaces 'ạ' with '?' (0x3F), causing generated CRC to differ from True UTF-8 CRC!
        System.out.println("Generated CRC (US_ASCII): " + generatedCrc);
        System.out.println("True UTF-8 CRC: " + trueUtf8Crc);
        
        // Verify computeCrc16CcittFalse correctly computes CRC over actual UTF-8 bytes for non-ASCII text
        assertThat(generatedCrc).isEqualTo(trueUtf8Crc);
    }

    @Test
    @DisplayName("Null bankBin or accountNo must throw NullPointerException")
    void testNullArgumentsThrowException() {
        assertThatThrownBy(() -> VietQrGeneratorUtil.generateEmvCoQr(null, "123", 1000, "REF"))
            .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> VietQrGeneratorUtil.generateEmvCoQr("970422", null, 1000, "REF"))
            .isInstanceOf(NullPointerException.class);
    }
}
