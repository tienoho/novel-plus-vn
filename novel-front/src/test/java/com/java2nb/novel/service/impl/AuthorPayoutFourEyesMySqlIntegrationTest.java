package com.java2nb.novel.service.impl;

import com.java2nb.novel.FrontNovelApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
    classes = FrontNovelApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {"spring.task.scheduling.enabled=false", "management.health.mail.enabled=false"}
)
@EnabledIfSystemProperty(named = "author.payout.four.eyes.mysql.it", matches = "true")
class AuthorPayoutFourEyesMySqlIntegrationTest {

    private static final long ID_BASE = 9_986_000_000L;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> withdrawalIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long id : withdrawalIds) {
            jdbcTemplate.update("DELETE FROM author_withdrawal_audit WHERE withdrawal_request_id = ?", id);
            jdbcTemplate.update("DELETE FROM author_withdrawal_request WHERE id = ?", id);
        }
    }

    @Test
    void databaseRejectsApproverAsExecutor() {
        long id = seedApprovedWithdrawal(1L, 101L);

        int updated = startProcessing(id, 1L, 101L);

        assertThat(updated).isZero();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM author_withdrawal_request WHERE id = ?", String.class, id))
            .isEqualTo("APPROVED");
    }

    @Test
    void onlyOneDifferentExecutorCanClaimAnApprovedWithdrawal() throws Exception {
        long id = seedApprovedWithdrawal(1L, 101L);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = pool.submit(() -> raceStartProcessing(id, 201L, ready, start));
            Future<Integer> second = pool.submit(() -> raceStartProcessing(id, 202L, ready, start));
            ready.await();
            start.countDown();

            assertThat(first.get() + second.get()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }

        Long executor = jdbcTemplate.queryForObject(
            "SELECT executed_by FROM author_withdrawal_request WHERE id = ?", Long.class, id);
        assertThat(executor).isIn(201L, 202L);
    }

    @Test
    void checkConstraintRejectsEqualPhaseActorsEvenWhenSqlGuardIsBypassed() {
        long id = seedApprovedWithdrawal(1L, 101L);

        assertThatThrownBy(() -> jdbcTemplate.update(
            "UPDATE author_withdrawal_request SET executed_by = approved_by WHERE id = ?", id))
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining("chk_author_withdrawal_four_eyes");
    }

    private int raceStartProcessing(long id, long executorId, CountDownLatch ready,
                                    CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        return startProcessing(id, 1L, executorId);
    }

    private int startProcessing(long id, long expectedVersion, long executorId) {
        return jdbcTemplate.update("""
            UPDATE author_withdrawal_request
            SET status = 'PROCESSING', executed_by = ?, version = version + 1
            WHERE id = ? AND status = 'APPROVED' AND version = ?
              AND approved_by IS NOT NULL AND approved_by <> ? AND executed_by IS NULL
            """, executorId, id, expectedVersion, executorId);
    }

    private long seedApprovedWithdrawal(long version, long approverId) {
        long id = ID_BASE + Math.floorMod(System.nanoTime(), 900_000L);
        withdrawalIds.add(id);
        String suffix = Long.toString(id);
        jdbcTemplate.update("""
            INSERT INTO author_withdrawal_request
                (id, withdrawal_no, idempotency_key, author_id, user_id, kyc_profile_id,
                 requested_xu, vnd_per_xu, gross_amount_vnd, withheld_tax_vnd, net_amount_vnd,
                 bank_code, bank_account_ciphertext, bank_account_last4,
                 bank_account_name_ciphertext, hold_idempotency_key, status, payout_provider,
                 requested_at, reviewed_at, reviewed_by, approved_by, version)
            VALUES (?, ?, ?, 9986001, 9986002, 9986003,
                    1000, 100, 100000, 10000, 90000,
                    'TEST', 'ciphertext', '1234', 'name-ciphertext', ?, 'APPROVED',
                    'MANUAL_BANK', NOW(3), NOW(3), ?, ?, ?)
            """, id, "WD-4EYES-" + suffix, "WD-4EYES-IDEM-" + suffix,
            "WD-4EYES-HOLD-" + suffix, approverId, approverId, version);
        return id;
    }
}
