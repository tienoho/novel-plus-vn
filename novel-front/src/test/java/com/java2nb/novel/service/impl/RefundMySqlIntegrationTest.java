package com.java2nb.novel.service.impl;

import com.java2nb.novel.FrontNovelApplication;
import com.java2nb.novel.entity.OrderRefund;
import com.java2nb.novel.service.RefundService;
import com.java2nb.novel.service.wallet.WalletLedgerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

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
@EnabledIfSystemProperty(named = "p0.mysql.it", matches = "true")
@Transactional
@Rollback
class RefundMySqlIntegrationTest {

    private static final long ADMIN_ID = 1L;
    private static final long CONFIRM_USER_ID = 9_900_001L;
    private static final long FAIL_USER_ID = 9_900_002L;
    private static final long CHARGEBACK_USER_ID = 9_900_003L;
    private static final long AUTHOR_ID = 9_900_004L;
    private static final long CONFIRM_ORDER = 9_900_001_001L;
    private static final long FAIL_ORDER = 9_900_001_002L;
    private static final long CHARGEBACK_ORDER = 9_900_001_003L;

    @Autowired
    private RefundService refundService;

    @Autowired
    private WalletLedgerService walletLedgerService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void refundAndChargebackPreserveLedgerInvariantsOnMySql() {
        seedPaidTopUp(CONFIRM_USER_ID, CONFIRM_ORDER, 500);
        seedPaidTopUp(FAIL_USER_ID, FAIL_ORDER, 500);
        seedPaidTopUp(CHARGEBACK_USER_ID, CHARGEBACK_ORDER, 500);

        verifyProviderConfirmedRefund();
        verifyProviderFailedRefund();
        verifyChargebackDebtAndRecovery();
        verifyLedgerAndProjectionInvariants();
        verifyRefundAuditIsImmutable();
    }

    private void verifyProviderConfirmedRefund() {
        OrderRefund requested = refundService.requestRefund(CONFIRM_USER_ID, CONFIRM_ORDER,
            "Độc giả yêu cầu hoàn tiền");
        OrderRefund approved = refundService.approveRefund(requested.getId(), ADMIN_ID);

        assertThat(approved.getStatus()).isEqualTo("APPROVED");
        assertThat(approved.getHoldLedgerTransactionId()).isNotNull();
        assertThat(walletBalance("USER", CONFIRM_USER_ID, "READER_XU")).isZero();
        assertThat(walletBalance("SYSTEM", 0L, "REFUND_CLEARING")).isEqualTo(500L);

        OrderRefund confirmed = refundService.confirmRefund(requested.getId(), "VNPAY-P0-IT-CONFIRMED", ADMIN_ID);

        assertThat(confirmed.getStatus()).isEqualTo("REVERSED");
        assertThat(confirmed.getProviderReference()).isEqualTo("VNPAY-P0-IT-CONFIRMED");
        assertThat(confirmed.getReversalLedgerTransactionId()).isNotNull();
        assertThat(walletBalance("SYSTEM", 0L, "REFUND_CLEARING")).isZero();
    }

    private void verifyProviderFailedRefund() {
        OrderRefund requested = refundService.requestRefund(FAIL_USER_ID, FAIL_ORDER,
            "Độc giả yêu cầu hoàn tiền");
        refundService.approveRefund(requested.getId(), ADMIN_ID);
        OrderRefund failed = refundService.failRefund(requested.getId(), "Provider từ chối hoàn tiền", ADMIN_ID);

        assertThat(failed.getStatus()).isEqualTo("FAILED");
        assertThat(failed.getReversalLedgerTransactionId()).isNotNull();
        assertThat(walletBalance("USER", FAIL_USER_ID, "READER_XU")).isEqualTo(500L);
        assertThat(walletBalance("SYSTEM", 0L, "REFUND_CLEARING")).isZero();
    }

    private void verifyChargebackDebtAndRecovery() {
        walletLedgerService.purchaseChapter(CHARGEBACK_USER_ID, AUTHOR_ID, 400, 280,
            "P0-IT-CHAPTER", "P0_IT_PURCHASE:" + CHARGEBACK_ORDER);

        OrderRefund chargeback = refundService.recordChargeback(CHARGEBACK_ORDER, 50_000,
            "Ngân hàng xác nhận chargeback", ADMIN_ID);

        assertThat(chargeback.getStatus()).isEqualTo("REVERSED");
        assertThat(walletBalance("USER", CHARGEBACK_USER_ID, "READER_XU")).isEqualTo(-400L);
        assertThat(walletStatus(CHARGEBACK_USER_ID)).isEqualTo("DEBT");

        walletLedgerService.creditReaderTopUp(CHARGEBACK_USER_ID, 200, "P0-IT-DEBT-RECOVERY-1",
            "P0_IT_DEBT_RECOVERY:1");
        assertThat(walletBalance("USER", CHARGEBACK_USER_ID, "READER_XU")).isEqualTo(-200L);
        assertThat(walletStatus(CHARGEBACK_USER_ID)).isEqualTo("DEBT");

        walletLedgerService.creditReaderTopUp(CHARGEBACK_USER_ID, 300, "P0-IT-DEBT-RECOVERY-2",
            "P0_IT_DEBT_RECOVERY:2");
        assertThat(walletBalance("USER", CHARGEBACK_USER_ID, "READER_XU")).isEqualTo(100L);
        assertThat(walletStatus(CHARGEBACK_USER_ID)).isEqualTo("ACTIVE");
    }

    private void verifyLedgerAndProjectionInvariants() {
        Integer unbalancedTransactions = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM (
                SELECT we.ledger_transaction_id
                FROM wallet_entry we
                WHERE we.ledger_transaction_id IN (
                    SELECT DISTINCT we2.ledger_transaction_id
                    FROM wallet_entry we2
                    JOIN wallet_account wa2 ON wa2.id = we2.wallet_account_id
                    WHERE wa2.owner_type = 'USER'
                      AND wa2.owner_id IN (?, ?, ?)
                )
                GROUP BY we.ledger_transaction_id
                HAVING SUM(we.amount) <> 0
            ) unbalanced
            """, Integer.class, CONFIRM_USER_ID, FAIL_USER_ID, CHARGEBACK_USER_ID);
        assertThat(unbalancedTransactions).isZero();

        Integer projectionMismatches = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM user u
            JOIN wallet_account wa
              ON wa.owner_type = 'USER'
             AND wa.owner_id = u.id
             AND wa.account_type = 'READER_XU'
            WHERE u.id IN (?, ?, ?)
              AND u.account_balance <> wa.available_balance
            """, Integer.class, CONFIRM_USER_ID, FAIL_USER_ID, CHARGEBACK_USER_ID);
        assertThat(projectionMismatches).isZero();
    }

    private void verifyRefundAuditIsImmutable() {
        Long auditId = jdbcTemplate.queryForObject("""
            SELECT id
            FROM order_refund_audit
            WHERE refund_no = ?
            ORDER BY id
            LIMIT 1
            """, Long.class, "RF-" + CONFIRM_ORDER);

        assertThat(auditId).isNotNull();
        assertThatThrownBy(() -> jdbcTemplate.update(
            "UPDATE order_refund_audit SET reason = 'tamper' WHERE id = ?", auditId))
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining("order_refund_audit is immutable");
    }

    private void seedPaidTopUp(long userId, long outTradeNo, int xu) {
        jdbcTemplate.update("""
            INSERT INTO user
                (id, username, password, nick_name, account_balance, status, create_time, update_time)
            VALUES (?, ?, 'p0-integration-test', 'P0 integration test', 0, 0, NOW(), NOW())
            """, userId, "p0_it_" + userId);
        jdbcTemplate.update("""
            INSERT INTO order_pay
                (out_trade_no, trade_no, pay_channel, total_amount, account_amount, user_id,
                 pay_status, create_time, update_time)
            VALUES (?, ?, 1, 50000, ?, ?, 1, NOW(), NOW())
            """, outTradeNo, "P0-IT-TRADE-" + outTradeNo, xu, userId);
        walletLedgerService.creditReaderTopUp(userId, xu, Long.toString(outTradeNo),
            "VNPAY_TOP_UP:" + outTradeNo);
    }

    private long walletBalance(String ownerType, long ownerId, String accountType) {
        Long balance = jdbcTemplate.queryForObject("""
            SELECT available_balance
            FROM wallet_account
            WHERE owner_type = ? AND owner_id = ? AND account_type = ? AND currency = 'XU'
            """, Long.class, ownerType, ownerId, accountType);
        return balance == null ? 0L : balance;
    }

    private String walletStatus(long userId) {
        return jdbcTemplate.queryForObject("""
            SELECT status
            FROM wallet_account
            WHERE owner_type = 'USER' AND owner_id = ? AND account_type = 'READER_XU' AND currency = 'XU'
            """, String.class, userId);
    }
}
