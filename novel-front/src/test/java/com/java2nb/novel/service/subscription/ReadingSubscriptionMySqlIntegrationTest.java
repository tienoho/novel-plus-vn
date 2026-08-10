package com.java2nb.novel.service.subscription;

import com.java2nb.novel.FrontNovelApplication;
import com.java2nb.novel.mapper.ReadingSubscriptionMandateMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;
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
@EnabledIfSystemProperty(named = "reader.subscription.mysql.it", matches = "true")
@Transactional
@Rollback
class ReadingSubscriptionMySqlIntegrationTest {
    @Autowired private ReadingSubscriptionService service;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ReadingSubscriptionRenewalService renewalService;
    @Autowired private ReadingSubscriptionMandateMapper mandateMapper;

    @Test
    void activationAndPeriodGrantAreAtomicAndIdempotent() {
        long suffix = Math.floorMod(System.nanoTime(), 800_000L);
        long userId = 99_780_000_000L + suffix;
        String planCode = "MYSQL_" + suffix;
        Date start = Date.from(Instant.parse("2027-01-01T00:00:00Z"));
        Date end = Date.from(Instant.parse("2027-04-01T00:00:00Z"));
        jdbcTemplate.update("""
            INSERT INTO reading_subscription_plan
                (plan_code, plan_name, price_vnd, tickets_per_period, period_months,
                 ticket_validity_days, status)
            VALUES (?, 'MySQL integration plan', 49000, 10, 1, 45, 'ACTIVE')
            """, planCode);

        ReadingSubscriptionRow activated = service.activate(
            new ReadingSubscriptionActivationCommand(userId, planCode, start, end,
                "ADMIN", "mysql-it-" + suffix, "v1"));
        ReadingSubscriptionGrantResult posted = service.grantDuePeriod(
            activated.getId(), start, ZoneId.of("UTC"), "v1");
        ReadingSubscriptionGrantResult replay = service.grantDuePeriod(
            activated.getId(), start, ZoneId.of("UTC"), "v1");

        assertThat(posted.status()).isEqualTo(ReadingSubscriptionGrantStatus.POSTED);
        assertThat(replay.status()).isEqualTo(ReadingSubscriptionGrantStatus.NOT_DUE);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT available_balance FROM reading_ticket_account WHERE user_id=?
            """, Long.class, userId)).isEqualTo(10L);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM reading_ticket_ledger
            WHERE user_id=? AND entry_type='GRANT' AND business_type='SUBSCRIPTION'
            """, Integer.class, userId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM reading_subscription_period_grant
            WHERE subscription_id=?
            """, Integer.class, activated.getId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT next_grant_at FROM user_reading_subscription WHERE id=?
            """, Date.class, activated.getId()).getTime())
            .isEqualTo(Date.from(Instant.parse("2027-02-01T00:00:00Z")).getTime());
        assertThat(service.listActivePlans())
            .anyMatch(plan -> planCode.equals(plan.getPlanCode()));
        assertThat(service.getCurrentSubscription(userId).getId()).isEqualTo(activated.getId());
        assertThat(service.listPeriodGrants(userId, activated.getId(), 25)).hasSize(1);
        assertThat(service.listPeriodGrants(userId + 1, activated.getId(), 25)).isEmpty();
    }

    @Test
    void adminPlanLifecycleIsOptimisticAndRetirementIsTerminal() {
        long suffix = Math.floorMod(System.nanoTime(), 800_000L);
        String planCode = "ADMIN_" + suffix;
        ReadingSubscriptionPlanCommand draft = new ReadingSubscriptionPlanCommand(
            planCode, "Gói quản trị MySQL", 79_000, 20, 1, 60);

        ReadingSubscriptionPlanRow created = service.createPlan(draft);
        assertThat(created.getStatus()).isEqualTo("DRAFT");
        ReadingSubscriptionPlanRow updated = service.updatePlan(created.getId(),
            created.getVersion(), new ReadingSubscriptionPlanCommand(
                planCode, "Gói quản trị đã sửa", 89_000, 25, 1, 60));
        ReadingSubscriptionPlanRow active = service.changePlanStatus(
            updated.getId(), updated.getVersion(), "ACTIVE");
        ReadingSubscriptionPlanRow retired = service.changePlanStatus(
            active.getId(), active.getVersion(), "RETIRED");

        assertThat(retired.getStatus()).isEqualTo("RETIRED");
        assertThatThrownBy(() -> service.changePlanStatus(
            retired.getId(), retired.getVersion(), "ACTIVE"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("RETIRED");
    }

    @Test
    void failedMandateReleasesOpenSlotForRetry() {
        long suffix = Math.floorMod(System.nanoTime(), 800_000L);
        long userId = 99_779_000_000L + suffix;
        String firstReference = "MYSQLMANDATEFAIL" + suffix;
        String retryReference = "MYSQLMANDATERETRY" + suffix;

        assertThat(mandateMapper.insertPending(userId, firstReference,
            "mandate_fail_" + suffix, "a".repeat(64), "MONTHLY", 1L)).isEqualTo(1);
        ReadingSubscriptionMandateRow first =
            mandateMapper.selectByMerchantReference(firstReference);
        assertThat(mandateMapper.fail(first.getId(), first.getVersion())).isEqualTo(1);

        assertThat(mandateMapper.insertPending(userId, retryReference,
            "mandate_retry_" + suffix, "b".repeat(64), "MONTHLY", 1L)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT open_user_id FROM reading_subscription_mandate
            WHERE merchant_reference=?
            """, Long.class, firstReference)).isNull();
        assertThat(mandateMapper.selectByMerchantReference(retryReference).getStatus())
            .isEqualTo("PENDING");
    }

    @Test
    void mandateInitializationSnapshotIsImmutableAndReplayDataIsClearedOnFailure() {
        long suffix = Math.floorMod(System.nanoTime(), 800_000L);
        long userId = 99_778_000_000L + suffix;
        String merchantReference = "MYSQLMANDATEIDEMP" + suffix;
        String duplicateReference = "MYSQLMANDATEDUP" + suffix;
        String clientRequestId = "mandate_idemp_" + suffix;
        String requestHash = "c".repeat(64);

        assertThat(mandateMapper.insertPending(userId, merchantReference,
            clientRequestId, requestHash, "MONTHLY", 3L)).isEqualTo(1);
        assertThat(mandateMapper.registerProviderInitialization(merchantReference,
            "666821925535879168", "v1:encrypted-data-key")).isEqualTo(1);

        ReadingSubscriptionMandateRow initialized =
            mandateMapper.selectByClientRequestId(userId, clientRequestId);
        assertThat(initialized.getRequestHash()).isEqualTo(requestHash);
        assertThat(initialized.getPlanCodeSnapshot()).isEqualTo("MONTHLY");
        assertThat(initialized.getAcceptedPlanVersion()).isEqualTo(3L);
        assertThat(initialized.getProviderDataKeyCiphertext()).isEqualTo("v1:encrypted-data-key");

        assertThatThrownBy(() -> jdbcTemplate.update("""
            UPDATE reading_subscription_mandate
            SET plan_code_snapshot='OTHER' WHERE id=?
            """, initialized.getId()))
            .isInstanceOf(org.springframework.dao.DataAccessException.class)
            .hasMessageContaining("snapshot idempotency");

        assertThat(mandateMapper.fail(initialized.getId(), initialized.getVersion())).isEqualTo(1);
        ReadingSubscriptionMandateRow failed = mandateMapper.selectById(initialized.getId());
        assertThat(failed.getStatus()).isEqualTo("FAILED");
        assertThat(failed.getProviderDataKeyCiphertext()).isNull();

        assertThatThrownBy(() -> mandateMapper.insertPending(userId, duplicateReference,
            clientRequestId, requestHash, "MONTHLY", 3L))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class)
            .hasMessageContaining("uk_rs_mandate_client_request");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void twoWorkersClaimExactlyOneProviderAttempt() throws Exception {
        long suffix = Math.floorMod(System.nanoTime(), 800_000L);
        long userId = 99_781_000_000L + suffix;
        String planCode = "RENEW_" + suffix;
        Date start = Date.from(Instant.parse("2027-01-01T00:00:00Z"));
        Date renewalAt = Date.from(Instant.parse("2027-02-01T00:00:00Z"));
        ReadingSubscriptionPlanRow draft = service.createPlan(new ReadingSubscriptionPlanCommand(
            planCode, "Gói recurring MySQL", 49_000, 500L, 10, 1, 45));
        ReadingSubscriptionPlanRow active = service.changePlanStatus(
            draft.getId(), draft.getVersion(), "ACTIVE");
        ReadingSubscriptionRow subscription = service.activate(new ReadingSubscriptionActivationCommand(
            userId, planCode, start, renewalAt, "ADMIN", "mysql-renew-" + suffix, "v1"));
        jdbcTemplate.update("""
            UPDATE user_reading_subscription
            SET auto_renew=1, primary_funding_source='VNPAY_RECURRING',
                fallback_funding_source='WALLET_XU', next_renewal_at=?,
                current_period_start=?, current_period_end=?, accepted_plan_version=?,
                price_vnd_snapshot=49000, price_xu_snapshot=500, status='ACTIVE'
            WHERE id=?
            """, renewalAt, start, renewalAt, active.getPlanVersion(), subscription.getId());
        jdbcTemplate.update("""
            INSERT INTO reading_subscription_mandate
                (user_id, provider, merchant_reference, provider_recurring_id,
                 provider_token_ciphertext, token_expire_at, status, consented_at)
            VALUES (?, 'VNPAY_RECURRING', ?, '666821925535879168',
                    'v1:integration-ciphertext', '2028-01-01', 'ACTIVE', NOW(3))
            """, userId, "MYSQLMANDATE" + suffix);
        try {
            assertThat(renewalService.prepareCycle(subscription.getId(), renewalAt, ZoneId.of("UTC")))
                .isEqualTo(ReadingSubscriptionRenewalResult.CYCLE_CREATED);
            Long cycleId = jdbcTemplate.queryForObject("""
                SELECT id FROM reading_subscription_renewal_cycle WHERE subscription_id=?
                """, Long.class, subscription.getId());
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch startGate = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<ReadingSubscriptionRenewalResult> first = executor.submit(() -> {
                    ready.countDown();
                    startGate.await();
                    return renewalService.processCycle(cycleId, renewalAt);
                });
                Future<ReadingSubscriptionRenewalResult> second = executor.submit(() -> {
                    ready.countDown();
                    startGate.await();
                    return renewalService.processCycle(cycleId, renewalAt);
                });
                ready.await();
                startGate.countDown();
                assertThat(List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder(ReadingSubscriptionRenewalResult.PROVIDER_CLAIMED,
                        ReadingSubscriptionRenewalResult.NOT_DUE);
            } finally {
                executor.shutdownNow();
            }
            assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM reading_subscription_renewal_attempt WHERE cycle_id=?
                """, Integer.class, cycleId)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("""
                SELECT status FROM reading_subscription_renewal_cycle WHERE id=?
                """, String.class, cycleId)).isEqualTo("PROCESSING");
            assertThat(jdbcTemplate.queryForObject("""
                SELECT status FROM user_reading_subscription WHERE id=?
                """, String.class, subscription.getId())).isEqualTo("PAST_DUE");
            ReadingSubscriptionProviderCharge providerCharge = renewalService.getProviderCharge(cycleId);
            assertThat(providerCharge.attemptNo()).isEqualTo(1);
            assertThat(providerCharge.amountVnd()).isEqualTo(49_000L);
            assertThat(providerCharge.providerRecurringId()).isEqualTo("666821925535879168");
            assertThat(renewalService.recordProviderPending(
                cycleId, 1, "UNAVAILABLE", renewalAt))
                .isEqualTo(ReadingSubscriptionRenewalResult.PROVIDER_PENDING);
            Date nextQueryAt = Date.from(Instant.parse("2027-02-01T00:05:00Z"));
            CountDownLatch queryReady = new CountDownLatch(2);
            CountDownLatch queryGate = new CountDownLatch(1);
            ExecutorService queryExecutor = Executors.newFixedThreadPool(2);
            try {
                Future<ReadingSubscriptionProviderQuery> firstQuery = queryExecutor.submit(() -> {
                    queryReady.countDown();
                    queryGate.await();
                    return renewalService.claimProviderQuery(cycleId, renewalAt, nextQueryAt);
                });
                Future<ReadingSubscriptionProviderQuery> secondQuery = queryExecutor.submit(() -> {
                    queryReady.countDown();
                    queryGate.await();
                    return renewalService.claimProviderQuery(cycleId, renewalAt, nextQueryAt);
                });
                queryReady.await();
                queryGate.countDown();
                List<ReadingSubscriptionProviderQuery> claims = java.util.Arrays.asList(
                    firstQuery.get(), secondQuery.get());
                assertThat(claims).filteredOn(java.util.Objects::nonNull).singleElement()
                    .satisfies(query -> {
                        assertThat(query.providerRequestId()).isEqualTo("NPR" + cycleId + "A1");
                        assertThat(query.amountVnd()).isEqualTo(49_000L);
                    });
            } finally {
                queryExecutor.shutdownNow();
            }
            assertThat(renewalService.recordProviderQueryFailure(
                cycleId, 1, "TX_02", renewalAt))
                .isEqualTo(ReadingSubscriptionRenewalResult.RETRY_SCHEDULED);
            Long retryVersion = jdbcTemplate.queryForObject(
                "SELECT version FROM reading_subscription_renewal_cycle WHERE id=?",
                Long.class, cycleId);
            Date retryAt = Date.from(Instant.parse("2027-02-02T00:00:00Z"));
            assertThat(renewalService.adminScheduleRetry(cycleId, retryVersion, 99L,
                "Đã xác minh provider thất bại cuối cùng", retryAt))
                .isEqualTo(ReadingSubscriptionRenewalResult.RETRY_SCHEDULED);
            Long auditId = jdbcTemplate.queryForObject("""
                SELECT id FROM reading_subscription_renewal_admin_audit
                WHERE cycle_id=? ORDER BY id DESC LIMIT 1
                """, Long.class, cycleId);
            assertThatThrownBy(() -> jdbcTemplate.update("""
                UPDATE reading_subscription_renewal_admin_audit
                SET reason='Không được sửa' WHERE id=?
                """, auditId)).isInstanceOf(org.springframework.dao.DataAccessException.class)
                .hasMessageContaining("reading_subscription_renewal_admin_audit is immutable");
        } finally {
            jdbcTemplate.update("DELETE FROM reading_subscription_renewal_attempt WHERE cycle_id IN "
                + "(SELECT id FROM reading_subscription_renewal_cycle WHERE subscription_id=?)",
                subscription.getId());
            jdbcTemplate.update("DELETE FROM reading_subscription_renewal_cycle WHERE subscription_id=?",
                subscription.getId());
            jdbcTemplate.update("DELETE FROM user_reading_subscription WHERE id=?", subscription.getId());
            jdbcTemplate.update("DELETE FROM reading_subscription_mandate WHERE user_id=?", userId);
            jdbcTemplate.update("DELETE FROM reading_subscription_plan WHERE id=?", active.getId());
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void twoWorkersClaimExactlyOneMandateRevocation() throws Exception {
        long suffix = Math.floorMod(System.nanoTime(), 800_000L);
        long userId = 99_783_000_000L + suffix;
        String merchantReference = "MYSQLREVOKE" + suffix;
        Date now = Date.from(Instant.parse("2027-02-01T00:00:00Z"));
        Date leaseUntil = Date.from(Instant.parse("2027-02-01T00:01:00Z"));
        jdbcTemplate.update("""
            INSERT INTO reading_subscription_mandate
                (user_id, provider, merchant_reference, provider_recurring_id,
                 provider_token_ciphertext, status, consented_at)
            VALUES (?, 'VNPAY_RECURRING', ?, '666821925535879168',
                    'v1:integration-ciphertext', 'ACTIVE', NOW(3))
            """, userId, merchantReference);
        try {
            assertThat(mandateMapper.requestRevocation(userId, now)).isEqualTo(1);
            Long mandateId = jdbcTemplate.queryForObject("""
                SELECT id FROM reading_subscription_mandate WHERE merchant_reference=?
                """, Long.class, merchantReference);
            assertThat(mandateMapper.selectDueRevocationIds(now, 10)).contains(mandateId);

            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch startGate = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<Integer> first = executor.submit(() -> {
                    ready.countDown();
                    startGate.await();
                    return mandateMapper.claimRevocation(mandateId, now, leaseUntil);
                });
                Future<Integer> second = executor.submit(() -> {
                    ready.countDown();
                    startGate.await();
                    return mandateMapper.claimRevocation(mandateId, now, leaseUntil);
                });
                ready.await();
                startGate.countDown();
                assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(1, 0);
            } finally {
                executor.shutdownNow();
            }
        assertThat(jdbcTemplate.queryForObject("""
            SELECT revoke_attempt_count FROM reading_subscription_mandate WHERE id=?
            """, Integer.class, mandateId)).isEqualTo(1);
        ReadingSubscriptionMandateRow claimed = mandateMapper.selectById(mandateId);
        assertThatThrownBy(() -> service.adminScheduleMandateRevocationRetry(
            mandateId, claimed.getVersion(), 99L, "Worker vẫn đang giữ lease xử lý", now))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("worker");

        Date adminRetryAt = leaseUntil;
        assertThat(service.adminScheduleMandateRevocationRetry(
            mandateId, claimed.getVersion(), 99L,
            "Đã xác minh provider có thể nhận retry", adminRetryAt))
            .isEqualTo(ReadingSubscriptionMandateAdminResult.RETRY_SCHEDULED);
        assertThat(service.listMandateQueue("REVOKE_PENDING", 10))
            .extracting(ReadingSubscriptionMandateQueueItem::getMandateId)
            .contains(mandateId);
        assertThat(service.listMandateAdminAudits(mandateId, 10)).singleElement()
            .satisfies(audit -> {
                assertThat(audit.getOperatorId()).isEqualTo(99L);
                assertThat(audit.getAction()).isEqualTo("RETRY_SCHEDULED");
            });
        Long auditId = jdbcTemplate.queryForObject("""
            SELECT id FROM reading_subscription_mandate_admin_audit
            WHERE mandate_id=? ORDER BY id DESC LIMIT 1
            """, Long.class, mandateId);
        assertThatThrownBy(() -> jdbcTemplate.update("""
            UPDATE reading_subscription_mandate_admin_audit
            SET reason='Không được sửa' WHERE id=?
            """, auditId)).isInstanceOf(org.springframework.dao.DataAccessException.class)
            .hasMessageContaining("reading_subscription_mandate_admin_audit is immutable");

        Date secondLease = Date.from(Instant.parse("2027-02-01T00:02:00Z"));
        assertThat(mandateMapper.claimRevocation(mandateId, adminRetryAt, secondLease)).isEqualTo(1);
        ReadingSubscriptionMandateRow retried = mandateMapper.selectById(mandateId);
        assertThat(mandateMapper.markRevoked(mandateId, retried.getVersion(), adminRetryAt))
            .isEqualTo(1);
        assertThat(mandateMapper.markRevoked(mandateId, retried.getVersion(), adminRetryAt)).isZero();
            assertThat(jdbcTemplate.queryForMap("""
                SELECT status, provider_token_ciphertext, token_expire_at, open_user_id
                FROM reading_subscription_mandate WHERE id=?
                """, mandateId)).containsEntry("status", "REVOKED")
                .containsEntry("provider_token_ciphertext", null)
                .containsEntry("token_expire_at", null)
                .containsEntry("open_user_id", null);
        } finally {
            jdbcTemplate.update("DELETE FROM reading_subscription_mandate WHERE user_id=?", userId);
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void queriedProviderSettlementIsIdempotent() {
        long suffix = Math.floorMod(System.nanoTime(), 800_000L);
        long userId = 99_782_000_000L + suffix;
        String planCode = "QUERYSETTLE_" + suffix;
        Date start = Date.from(Instant.parse("2027-01-01T00:00:00Z"));
        Date renewalAt = Date.from(Instant.parse("2027-02-01T00:00:00Z"));
        ReadingSubscriptionPlanRow draft = service.createPlan(new ReadingSubscriptionPlanCommand(
            planCode, "Gói QueryDr MySQL", 49_000, 500L, 10, 1, 45));
        ReadingSubscriptionPlanRow active = service.changePlanStatus(
            draft.getId(), draft.getVersion(), "ACTIVE");
        ReadingSubscriptionRow subscription = service.activate(new ReadingSubscriptionActivationCommand(
            userId, planCode, start, renewalAt, "ADMIN", "mysql-query-" + suffix, "v1"));
        jdbcTemplate.update("""
            UPDATE user_reading_subscription
            SET auto_renew=1, primary_funding_source='VNPAY_RECURRING',
                fallback_funding_source='WALLET_XU', next_renewal_at=?,
                current_period_start=?, current_period_end=?, accepted_plan_version=?,
                price_vnd_snapshot=49000, price_xu_snapshot=500, status='ACTIVE'
            WHERE id=?
            """, renewalAt, start, renewalAt, active.getPlanVersion(), subscription.getId());
        Long cycleId = null;
        try {
            assertThat(renewalService.prepareCycle(subscription.getId(), renewalAt, ZoneId.of("UTC")))
                .isEqualTo(ReadingSubscriptionRenewalResult.CYCLE_CREATED);
            cycleId = jdbcTemplate.queryForObject(
                "SELECT id FROM reading_subscription_renewal_cycle WHERE subscription_id=?",
                Long.class, subscription.getId());
            assertThat(renewalService.processCycle(cycleId, renewalAt))
                .isEqualTo(ReadingSubscriptionRenewalResult.PROVIDER_CLAIMED);
            assertThat(renewalService.recordProviderPending(cycleId, 1, "UNAVAILABLE", renewalAt))
                .isEqualTo(ReadingSubscriptionRenewalResult.PROVIDER_PENDING);
            Date nextQueryAt = Date.from(Instant.parse("2027-02-01T00:05:00Z"));
            assertThat(renewalService.claimProviderQuery(cycleId, renewalAt, nextQueryAt)).isNotNull();

            assertThat(renewalService.settleProviderQuery(
                cycleId, 1, "777821925535879168", nextQueryAt))
                .isEqualTo(ReadingSubscriptionRenewalResult.SETTLED);
            assertThat(renewalService.settleProviderQuery(
                cycleId, 1, "777821925535879168", nextQueryAt))
                .isEqualTo(ReadingSubscriptionRenewalResult.NOT_DUE);
            assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM reading_subscription_renewal_cycle WHERE id=?",
                String.class, cycleId)).isEqualTo("SETTLED");
            assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM reading_subscription_renewal_attempt
                WHERE cycle_id=? AND status='SETTLED' AND provider_transaction_id=?
                """, Integer.class, cycleId, "777821925535879168")).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                "SELECT current_period_end FROM user_reading_subscription WHERE id=?",
                Date.class, subscription.getId()).getTime())
                .isEqualTo(Date.from(Instant.parse("2027-03-01T00:00:00Z")).getTime());
        } finally {
            if (cycleId != null) {
                jdbcTemplate.update(
                    "DELETE FROM reading_subscription_renewal_attempt WHERE cycle_id=?", cycleId);
            }
            jdbcTemplate.update(
                "DELETE FROM reading_subscription_renewal_cycle WHERE subscription_id=?",
                subscription.getId());
            jdbcTemplate.update("DELETE FROM user_reading_subscription WHERE id=?", subscription.getId());
            jdbcTemplate.update("DELETE FROM reading_subscription_plan WHERE id=?", active.getId());
        }
    }
}
