package com.java2nb.novel.common.empirical;

import com.java2nb.novel.common.annotation.LimitType;
import com.java2nb.novel.common.annotation.RateLimit;
import com.java2nb.novel.common.aspect.RateLimitAspect;
import com.java2nb.novel.common.dao.FinancialVoucherDao;
import com.java2nb.novel.common.dao.User2faDao;
import com.java2nb.novel.common.entity.FinancialVoucherDO;
import com.java2nb.novel.common.entity.User2faDO;
import com.java2nb.novel.common.service.impl.FinancialVoucherServiceImpl;
import com.java2nb.novel.common.service.impl.TotpServiceImpl;
import com.java2nb.novel.common.tax.PitTaxCalculator;
import com.java2nb.novel.common.tax.PitTaxCalculatorService;
import com.java2nb.novel.common.tax.PitTaxResult;
import com.java2nb.novel.common.tax.VatTaxCalculatorService;
import com.java2nb.novel.common.tax.VatTaxResult;
import com.java2nb.novel.core.exception.BusinessException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class Milestone56EmpiricalChallengeTest {

    private PitTaxCalculatorService pitService;
    private VatTaxCalculatorService vatService;
    private FinancialVoucherServiceImpl voucherService;
    private FinancialVoucherDao voucherDao;
    private TotpServiceImpl totpService;
    private User2faDao user2faDao;

    @BeforeEach
    public void setUp() {
        pitService = new PitTaxCalculatorService();
        vatService = new VatTaxCalculatorService();
        voucherDao = mock(FinancialVoucherDao.class);
        voucherService = new FinancialVoucherServiceImpl(voucherDao, vatService, pitService);
        user2faDao = mock(User2faDao.class);
        totpService = new TotpServiceImpl(user2faDao);
    }

    // ==========================================
    // Task 1: PIT Tax Calculation Engine Tests
    // ==========================================

    @Test
    @DisplayName("PIT Task 1: Income below threshold (1,999,999 VND)")
    public void testPitIncomeBelowThreshold() {
        long gross = 1_999_999L;
        PitTaxResult result = pitService.calculatePit(gross);
        assertEquals(1_999_999L, result.getGrossAmountVnd());
        assertEquals(0L, result.getTaxableAmountVnd());
        assertEquals(0L, result.getWithheldTaxVnd());
        assertEquals(1_999_999L, result.getNetAmountVnd());
    }

    @Test
    @DisplayName("PIT Task 1: Empirical observation of 2,000,000 VND vs user spec requirement")
    public void testPitIncomeExactly2Million() {
        long gross = 2_000_000L;
        PitTaxResult result = pitService.calculatePit(gross);
        assertEquals(2_000_000L, result.getGrossAmountVnd());
        assertEquals(0L, result.getWithheldTaxVnd(), "Income up to 2,000,000 VND is exempt");
        assertEquals(2_000_000L, result.getNetAmountVnd());
    }

    @Test
    @DisplayName("PIT Task 1: Income at 2,000,001 VND")
    public void testPitIncome2MillionAndOne() {
        long gross = 2_000_001L;
        PitTaxResult result = pitService.calculatePit(gross);
        assertEquals(2_000_001L, result.getGrossAmountVnd());
        assertEquals(2_000_001L, result.getTaxableAmountVnd());
        assertEquals(200_000L, result.getWithheldTaxVnd(), "Math.round(2000001 * 0.1) = 200,000");
        assertEquals(1_800_001L, result.getNetAmountVnd());
    }

    @Test
    @DisplayName("PIT Task 1: Income at 5,000,000 VND")
    public void testPitIncome5Million() {
        long gross = 5_000_000L;
        PitTaxResult result = pitService.calculatePit(gross);
        assertEquals(5_000_000L, result.getGrossAmountVnd());
        assertEquals(5_000_000L, result.getTaxableAmountVnd());
        assertEquals(500_000L, result.getWithheldTaxVnd());
        assertEquals(4_500_000L, result.getNetAmountVnd());
    }

    // ==========================================
    // Task 2: VAT Separation Calculations
    // ==========================================

    @Test
    @DisplayName("VAT Task 2: Standard 110,000 VND top-up with 10% VAT")
    public void testVatSeparation110k() {
        long gross = 110_000L;
        VatTaxResult result = vatService.calculateVat(gross);
        assertEquals(110_000L, result.getGrossAmountVnd());
        assertEquals(100_000L, result.getNetAmountVnd());
        assertEquals(10_000L, result.getVatAmountVnd());
        assertEquals(gross, result.getNetAmountVnd() + result.getVatAmountVnd(), "Gross must equal Net + VAT");
    }

    @Test
    @DisplayName("VAT Task 2: 100,000 VND top-up rounding separation")
    public void testVatSeparation100k() {
        long gross = 100_000L;
        VatTaxResult result = vatService.calculateVat(gross);
        assertEquals(100_000L, result.getGrossAmountVnd());
        assertEquals(90_909L, result.getNetAmountVnd());
        assertEquals(9_091L, result.getVatAmountVnd());
        assertEquals(gross, result.getNetAmountVnd() + result.getVatAmountVnd(), "Gross must equal Net + VAT");
    }

    @Test
    @DisplayName("VAT Task 2: Invariant test across multiple top-up amounts")
    public void testVatInvariantAcrossAmounts() {
        long[] testAmounts = { 10_000L, 50_000L, 100_000L, 200_000L, 500_000L, 1_000_000L, 10_000_000L };
        for (long gross : testAmounts) {
            VatTaxResult res = vatService.calculateVat(gross);
            assertEquals(gross, res.getNetAmountVnd() + res.getVatAmountVnd(),
                    "Net + VAT must equal Gross for " + gross);
            assertTrue(res.getVatAmountVnd() >= 0);
            assertTrue(res.getNetAmountVnd() > 0);
        }
    }

    // ==========================================
    // Task 3: Financial Voucher Generation & Checksum
    // ==========================================

    @Test
    @DisplayName("Voucher Task 3: Checksum integrity and PDF export Sha256 format")
    public void testVoucherChecksumIntegrity() throws Exception {
        FinancialVoucherDO voucher = FinancialVoucherDO.builder()
                .voucherNo("INV-20260725-55555")
                .voucherType("RECHARGE_RECEIPT")
                .referenceType("ORDER_PAY")
                .referenceId("ORD-999")
                .payerName("Payer")
                .payeeName("Khoi-Thu")
                .grossAmountVnd(110_000L)
                .taxAmountVnd(10_000L)
                .netAmountVnd(100_000L)
                .status("ISSUED")
                .build();

        when(voucherDao.selectByVoucherNo("INV-20260725-55555")).thenReturn(voucher);

        byte[] pdfBytes = voucherService.exportVoucherPdf("INV-20260725-55555");

        String expectedPayload = "INV-20260725-55555|110000|100000";
        String expectedHash = computeSha256(expectedPayload);

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            String extractedText = new PDFTextStripper().getText(document);
            assertTrue(extractedText.contains("Mã kiểm tra: " + expectedHash),
                    "PDF output must include exact Sha256 checksum");
        }
    }

    @Test
    @DisplayName("Voucher Task 3: Challenge voucher numbering uniqueness collisions")
    public void testVoucherNumberingCollisionRisk() throws Exception {
        Method method = FinancialVoucherServiceImpl.class.getDeclaredMethod("generateVoucherNo", String.class);
        method.setAccessible(true);

        Set<String> generatedNos = new HashSet<>();
        int iterations = 10000;
        int collisions = 0;

        for (int i = 0; i < iterations; i++) {
            String vNo = (String) method.invoke(voucherService, "INV");
            if (!generatedNos.add(vNo)) {
                collisions++;
            }
        }

        assertEquals(0, collisions, "Mã chứng từ không được va chạm trong 10.000 lần sinh");
    }

    // ==========================================
    // Task 5: TOTP RFC 6238 Verification & Skew Window
    // ==========================================

    @Test
    @DisplayName("TOTP Task 5: Secret Key generation and Base32 properties")
    public void testTotpSecretKeyGeneration() {
        String secret = totpService.generateSecretKey();
        assertNotNull(secret);
        assertEquals(32, secret.length(), "160-bit secret in Base32 (20 bytes * 8 / 5) = 32 chars");
        assertTrue(secret.matches("[A-Z2-7]+"), "Must contain only Base32 valid chars");
    }

    @Test
    @DisplayName("TOTP Task 5: QR Code URI formatting according to spec")
    public void testTotpQrCodeUriFormatting() {
        String secret = "JBSWY3DPEHPK3PXP";
        String uri = totpService.getQrCodeUri("user@khoithu.vn", secret);
        assertEquals("otpauth://totp/NovelPlus:user@khoithu.vn?secret=JBSWY3DPEHPK3PXP&issuer=NovelPlus", uri);
    }

    @Test
    @DisplayName("TOTP Task 5: Code verification skew window (+/- 1 step window)")
    public void testTotpSkewWindowVerification() throws Exception {
        String secret = totpService.generateSecretKey();

        Method generateMethod = TotpServiceImpl.class.getDeclaredMethod("generateTotpCode", String.class, long.class);
        generateMethod.setAccessible(true);

        assertTotpOffset(generateMethod, secret, 0, true, "Current window code must be valid");
        assertTotpOffset(generateMethod, secret, -1, true,
                "Window T-1 code must be valid (30s skew)");
        assertTotpOffset(generateMethod, secret, 1, true,
                "Window T+1 code must be valid (30s skew)");
        assertTotpOffset(generateMethod, secret, -2, false,
                "Window T-2 code must be rejected (>30s skew)");
        assertTotpOffset(generateMethod, secret, 2, false,
                "Window T+2 code must be rejected (>30s skew)");
    }

    private void assertTotpOffset(Method generateMethod, String secret, int offset,
            boolean expected, String message) throws Exception {
        for (int attempt = 0; attempt < 3; attempt++) {
            long windowBefore = System.currentTimeMillis() / 1000L / 30L;
            String code = (String) generateMethod.invoke(totpService, secret, windowBefore + offset);
            boolean actual = totpService.verifyTotp(secret, code);
            long windowAfter = System.currentTimeMillis() / 1000L / 30L;
            if (windowBefore == windowAfter) {
                assertEquals(expected, actual, message);
                return;
            }
        }
        fail("Không thể kiểm tra TOTP trong cùng một cửa sổ thời gian ổn định");
    }

    // ==========================================
    // Task 6: RateLimit Aspect Fallback & Window Logic
    // ==========================================

    @Test
    @DisplayName("RateLimit Task 6: Fallback to in-memory window when Redis fails")
    public void testRateLimitRedisFallback() throws Throwable {
        RateLimitAspect aspect = new RateLimitAspect();

        // Mock RedisTemplate that throws exception on increment
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString(), anyLong())).thenThrow(new RuntimeException("Redis connection refused"));

        // Inject failing RedisTemplate via reflection
        java.lang.reflect.Field redisField = RateLimitAspect.class.getDeclaredField("redisTemplate");
        redisField.setAccessible(true);
        redisField.set(aspect, redisTemplate);

        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        when(signature.getDeclaringTypeName()).thenReturn("com.java2nb.novel.controller.TestController");
        when(signature.getName()).thenReturn("testMethod");
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.proceed()).thenReturn("OK");

        RateLimit rateLimit = mock(RateLimit.class);
        when(rateLimit.key()).thenReturn("fallback_test");
        when(rateLimit.count()).thenReturn(2);
        when(rateLimit.timeWindowSeconds()).thenReturn(60);
        when(rateLimit.limitType()).thenReturn(LimitType.GLOBAL);
        when(rateLimit.message()).thenReturn("Rate limit exceeded");

        // First 2 calls should succeed via in-memory fallback
        assertEquals("OK", aspect.around(joinPoint, rateLimit));
        assertEquals("OK", aspect.around(joinPoint, rateLimit));

        // 3rd call should throw BusinessException
        assertThrows(BusinessException.class, () -> aspect.around(joinPoint, rateLimit));
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
            return "";
        }
    }
}
