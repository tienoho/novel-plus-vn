package com.java2nb.novel.controller;

import com.java2nb.novel.FrontNovelApplication;
import com.java2nb.novel.common.service.FinancialVoucherService;
import com.java2nb.novel.core.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
    classes = FrontNovelApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.task.scheduling.enabled=false",
        "management.health.mail.enabled=false"
    }
)
@EnabledIfSystemProperty(named = "p0.voucher.mysql.it", matches = "true")
@Transactional
@Rollback
class FinancialVoucherOwnershipMySqlIntegrationTest {

    private static final long AUTHOR_ID = 9_984_001L;
    private static final long OTHER_AUTHOR_ID = 9_984_002L;
    private static final long WITHDRAWAL_ID = 9_984_101L;
    private static final long OTHER_WITHDRAWAL_ID = 9_984_102L;

    @Autowired
    private FinancialVoucherService voucherService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void authorCanOnlyListReadAndExportOwnedPayoutVouchers() {
        seedWithdrawal(WITHDRAWAL_ID, AUTHOR_ID, "OWN");
        seedWithdrawal(OTHER_WITHDRAWAL_ID, OTHER_AUTHOR_ID, "OTHER");
        seedVoucher("VOUCHER-OWNERSHIP-OWN", WITHDRAWAL_ID, "Tác giả sở hữu");
        seedVoucher("VOUCHER-OWNERSHIP-OTHER", OTHER_WITHDRAWAL_ID, "Tác giả khác");

        assertThat(voucherService.listAuthorVouchers(AUTHOR_ID))
            .extracting("voucherNo")
            .containsExactly("VOUCHER-OWNERSHIP-OWN");
        assertThat(voucherService.getAuthorVoucher("VOUCHER-OWNERSHIP-OWN", AUTHOR_ID))
            .isNotNull();
        assertThat(voucherService.getAuthorVoucher("VOUCHER-OWNERSHIP-OTHER", AUTHOR_ID))
            .isNull();

        byte[] pdf = voucherService.exportAuthorVoucherPdf(
            "VOUCHER-OWNERSHIP-OWN", AUTHOR_ID);
        assertThat(pdf).startsWith("%PDF-".getBytes(StandardCharsets.US_ASCII));
        assertThatThrownBy(() -> voucherService.exportAuthorVoucherPdf(
            "VOUCHER-OWNERSHIP-OTHER", AUTHOR_ID))
            .isInstanceOf(BusinessException.class);
    }

    private void seedWithdrawal(long id, long authorId, String suffix) {
        jdbcTemplate.update("""
            INSERT INTO author_withdrawal_request
                (id, withdrawal_no, idempotency_key, author_id, user_id, kyc_profile_id,
                 requested_xu, vnd_per_xu, gross_amount_vnd, withheld_tax_vnd, net_amount_vnd,
                 bank_code, bank_account_ciphertext, bank_account_last4,
                 bank_account_name_ciphertext, hold_idempotency_key, status, payout_provider,
                 requested_at)
            VALUES (?, ?, ?, ?, ?, ?, 1000, 100, 100000, 10000, 90000,
                    'TEST', 'ciphertext', '1234', 'name-ciphertext', ?, 'PAID',
                    'MANUAL_BANK', NOW(3))
            """, id, "WD-OWNERSHIP-" + suffix, "WD-OWNERSHIP-IDEM-" + suffix,
            authorId, authorId + 1000, id + 1000, "WD-OWNERSHIP-HOLD-" + suffix);
    }

    private void seedVoucher(String voucherNo, long withdrawalId, String payeeName) {
        jdbcTemplate.update("""
            INSERT INTO financial_voucher
                (voucher_no, voucher_type, reference_type, reference_id, payer_name,
                 payer_tax_code, payee_name, payee_tax_code, gross_amount_vnd,
                 tax_amount_vnd, net_amount_vnd, currency, status, issued_at)
            VALUES (?, 'AUTHOR_PAYOUT_VOUCHER', 'AUTHOR_WITHDRAWAL_REQUEST', ?,
                    'Khởi Thư', '0123456789', ?, 'N/A', 100000, 10000, 90000,
                    'VND', 'ISSUED', NOW(3))
            """, voucherNo, Long.toString(withdrawalId), payeeName);
    }
}
