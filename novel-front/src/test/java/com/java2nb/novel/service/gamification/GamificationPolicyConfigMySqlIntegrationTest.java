package com.java2nb.novel.service.gamification;

import com.java2nb.novel.FrontNovelApplication;
import com.java2nb.novel.service.gamification.config.GamificationConfigConflictException;
import com.java2nb.novel.service.gamification.config.GamificationPolicyBundleDetail;
import com.java2nb.novel.service.gamification.config.GamificationPolicyBundleRow;
import com.java2nb.novel.service.gamification.config.GamificationPolicyLifecycleService;
import com.java2nb.novel.service.gamification.config.GamificationRuntimeConfigLifecycleService;
import com.java2nb.novel.service.gamification.config.GamificationRuntimeConfigRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
@EnabledIfSystemProperty(named = "gamification.policy.mysql.it", matches = "true")
@Transactional
@Rollback
class GamificationPolicyConfigMySqlIntegrationTest {

    private static final long MAKER_ID = 90_101L;
    private static final long CHECKER_ID = 90_102L;

    @Autowired
    private GamificationPolicyLifecycleService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private GamificationRuntimeConfigLifecycleService runtimeConfigLifecycle;

    @Autowired
    private GamificationPublicPolicyService publicPolicyService;

    @Test
    void policyLifecycleRunsThroughMyBatisAndKeepsPublishedContentImmutable() {
        String policyVersion = uniqueVersion("policy");
        GamificationPolicyBundleDetail draft = createReadyDraft(policyVersion);
        draft = service.savePublicPolicy(policyVersion, draft.getBundle().getVersion(),
            "Luật chơi " + policyVersion,
            "Luật chơi tích hợp mô tả thời hạn Đuốc, xếp hạng, kiểm duyệt, khiếu nại và "
                + "quy trình nhận thưởng minh bạch cho toàn bộ độc giả.",
            MAKER_ID, "Cập nhật luật chơi trong policy bundle còn ở trạng thái nháp");
        LevelRuleRow levelTwo = draft.getLevels().stream()
            .filter(row -> Integer.valueOf(2).equals(row.getLevel()))
            .findFirst()
            .orElseThrow();
        levelTwo.setMinExp(levelTwo.getMinExp() + 1);

        GamificationPolicyBundleDetail changed = service.saveLevel(" " + policyVersion + " ",
            draft.getBundle().getVersion(), levelTwo, MAKER_ID,
            "Điều chỉnh ngưỡng level để kiểm tra MyBatis");
        GamificationPolicyBundleRow submitted = service.submit(policyVersion,
            changed.getBundle().getVersion(), MAKER_ID,
            "Gửi policy tích hợp để người thứ hai phê duyệt");
        assertThatThrownBy(() -> jdbcTemplate.update("""
            UPDATE gamification_public_policy SET content_text=CONCAT(content_text, ' changed')
            WHERE policy_version=?
            """, policyVersion))
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining("submitted public policy is immutable");
        GamificationPolicyBundleRow approved = service.approve(policyVersion,
            submitted.getVersion(), CHECKER_ID,
            "Đã kiểm tra diff và đồng ý phát hành policy");
        GamificationPolicyBundleRow published = service.publish(policyVersion,
            approved.getVersion(), CHECKER_ID,
            "Phát hành policy sau khi hoàn tất phê duyệt");

        assertThat(published.getStatus()).isEqualTo("PUBLISHED");
        assertThat(published.getContentHash()).hasSize(64);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM gamification_policy_bundle_audit
            WHERE policy_id=? AND event_type IN
                ('CREATED', 'CONTENT_UPDATED', 'SUBMITTED', 'APPROVED', 'PUBLISHED')
            """, Integer.class, published.getId())).isEqualTo(7);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM gamification_public_policy
            WHERE status='PUBLISHED'
            """, Integer.class)).isEqualTo(2);
        assertThat(publicPolicyService.getPublished().getPolicyVersion())
            .isEqualTo(runtimeConfigLifecycle.getActive().getPolicyVersion());
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM gamification_public_policy_audit policy_audit
            JOIN gamification_public_policy policy ON policy.id=policy_audit.policy_id
            WHERE policy.policy_version=? AND policy_audit.event_type='PUBLISHED'
            """, Integer.class, policyVersion)).isEqualTo(1);
        assertThatThrownBy(() -> jdbcTemplate.update("""
            UPDATE level_rule SET min_exp=min_exp+1
            WHERE rule_version=? AND level=2
            """, policyVersion))
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining("level rule policy is immutable");
        assertThatThrownBy(() -> jdbcTemplate.update("""
            UPDATE gamification_policy_bundle SET policy_version=? WHERE id=?
            """, policyVersion + "-changed", published.getId()))
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining("policy identity is immutable");
        assertThatThrownBy(() -> jdbcTemplate.update("""
            UPDATE gamification_policy_bundle SET status='DRAFT' WHERE id=?
            """, published.getId()))
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining("invalid gamification policy status transition");
        assertThatThrownBy(() -> jdbcTemplate.update("""
            INSERT INTO gamification_public_policy (policy_version, title, content_text, status)
            VALUES (?, 'Orphan policy', 'Nội dung không thuộc policy bundle hợp lệ', 'DRAFT')
            """, uniqueVersion("orphan")))
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining("requires a DRAFT policy bundle");
        assertThatThrownBy(() -> jdbcTemplate.update("""
            UPDATE gamification_policy_bundle_audit SET reason='tamper' WHERE policy_id=?
            """, published.getId()))
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining("gamification policy audit is immutable");
        assertThatThrownBy(() -> jdbcTemplate.update("""
            DELETE FROM gamification_policy_bundle_audit WHERE policy_id=?
            """, published.getId()))
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining("gamification policy audit is immutable");
    }

    @Test
    void staleVersionCannotOverwritePolicyDraft() {
        String policyVersion = uniqueVersion("stale");
        GamificationPolicyBundleDetail draft = createReadyDraft(policyVersion);
        LevelRuleRow levelOne = draft.getLevels().get(0);

        assertThatThrownBy(() -> service.saveLevel(policyVersion,
            draft.getBundle().getVersion() - 1, levelOne, MAKER_ID,
            "Thử ghi bằng expectedVersion đã hết hạn"))
            .isInstanceOf(GamificationConfigConflictException.class);
    }

    @Test
    void activeRuntimeSnapshotCannotBeMutatedOutsideLifecycle() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
            UPDATE gamification_runtime_config
            SET job_batch_size=job_batch_size+1
            WHERE status='ACTIVE'
            """))
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining("submitted gamification runtime config is immutable");
        assertThatThrownBy(() -> jdbcTemplate.update("""
            UPDATE gamification_runtime_config SET status='DRAFT'
            WHERE status='ACTIVE'
            """))
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining("invalid gamification runtime config status transition");
    }

    @Test
    void draftPolicyRowsCannotBeMovedIntoPublishedPolicyVersion() {
        String draftVersion = uniqueVersion("target");
        service.createDraft(draftVersion, "v1", MAKER_ID,
            "Tạo policy nháp để kiểm tra bảo vệ target version");
        jdbcTemplate.update("""
            INSERT INTO level_rule (rule_version, level, min_exp, title_key, frame_code)
            VALUES (?, 99, 999999, 'gamification.level.test', NULL)
            """, draftVersion);

        assertThatThrownBy(() -> jdbcTemplate.update("""
            UPDATE level_rule SET rule_version='v1'
            WHERE rule_version=? AND level=99
            """, draftVersion))
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining("target policy is immutable");
    }

    @Test
    void runtimeImportIsIdempotentAndRollbackCreatesAppendOnlyRevision() {
        GamificationRuntimeConfigRow active = runtimeConfigLifecycle.getActive();
        String sourceHash = String.format("%064x", System.nanoTime());

        GamificationRuntimeConfigRow imported = runtimeConfigLifecycle.importSnapshot(
            active.getSnapshot(), sourceHash, MAKER_ID,
            "Import snapshot ENV để kiểm tra idempotency");
        GamificationRuntimeConfigRow importedAgain = runtimeConfigLifecycle.importSnapshot(
            active.getSnapshot(), sourceHash, MAKER_ID,
            "Import lại cùng snapshot ENV không tạo revision mới");
        GamificationRuntimeConfigRow rollback = runtimeConfigLifecycle.rollbackFrom(
            active.getId(), MAKER_ID, "Tạo revision rollback từ ACTIVE hiện hành");

        assertThat(importedAgain.getId()).isEqualTo(imported.getId());
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM gamification_runtime_config WHERE source_hash=?
            """, Integer.class, sourceHash)).isEqualTo(1);
        assertThat(rollback.getId()).isNotEqualTo(active.getId());
        assertThat(rollback.getRevisionNo()).isGreaterThan(active.getRevisionNo());
        assertThat(rollback.getSourceRevisionId()).isEqualTo(active.getId());
        assertThat(jdbcTemplate.queryForObject("""
            SELECT status FROM gamification_runtime_config WHERE id=?
            """, String.class, active.getId())).isEqualTo("ACTIVE");

        GamificationRuntimeConfigRow submitted = runtimeConfigLifecycle.submit(imported.getId(),
            imported.getVersion(), MAKER_ID,
            "Gửi snapshot import để kiểm tra tính bất biến");
        assertThat(submitted.getStatus()).isEqualTo("PENDING_APPROVAL");
        assertThatThrownBy(() -> jdbcTemplate.update("""
            UPDATE gamification_runtime_config SET job_batch_size=job_batch_size+1 WHERE id=?
            """, submitted.getId()))
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining("submitted gamification runtime config is immutable");
        assertThatThrownBy(() -> jdbcTemplate.update("""
            UPDATE gamification_runtime_config_audit SET reason='tamper' WHERE config_id=?
            """, submitted.getId()))
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining("gamification runtime config audit is immutable");
        assertThatThrownBy(() -> jdbcTemplate.update("""
            DELETE FROM gamification_runtime_config_audit WHERE config_id=?
            """, submitted.getId()))
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining("gamification runtime config audit is immutable");
    }

    @Test
    void makerCannotApproveOwnHighRiskPolicy() {
        String policyVersion = uniqueVersion("maker");
        GamificationPolicyBundleDetail draft = createReadyDraft(policyVersion);
        GamificationPolicyBundleRow submitted = service.submit(policyVersion,
            draft.getBundle().getVersion(), MAKER_ID,
            "Gửi policy để kiểm tra nguyên tắc hai người");

        assertThatThrownBy(() -> service.approve(policyVersion, submitted.getVersion(), MAKER_ID,
            "Người tạo thử tự phê duyệt policy của mình"))
            .isInstanceOf(SecurityException.class);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void onlyOneScheduledRevisionAndOneActivationWorkerCanWin() throws Exception {
        GamificationRuntimeConfigRow first = runtimeConfigLifecycle.cloneActive(MAKER_ID,
            "Tạo revision thứ nhất để kiểm tra cạnh tranh activation");
        first = runtimeConfigLifecycle.submit(first.getId(), first.getVersion(), MAKER_ID,
            "Gửi revision thứ nhất vào quy trình kích hoạt");
        first = runtimeConfigLifecycle.approve(first.getId(), first.getVersion(), MAKER_ID,
            "Tự duyệt tuning rủi ro thấp cho integration test");
        Date effectiveAt = new Date(System.currentTimeMillis() + 1_000);
        first = runtimeConfigLifecycle.schedule(first.getId(), first.getVersion(), MAKER_ID,
            "Hẹn revision thứ nhất để hai worker cạnh tranh", effectiveAt);

        GamificationRuntimeConfigRow second = runtimeConfigLifecycle.cloneActive(MAKER_ID,
            "Tạo revision thứ hai để kiểm tra unique scheduled");
        second = runtimeConfigLifecycle.submit(second.getId(), second.getVersion(), MAKER_ID,
            "Gửi revision thứ hai vào quy trình kích hoạt");
        second = runtimeConfigLifecycle.approve(second.getId(), second.getVersion(), MAKER_ID,
            "Tự duyệt tuning thứ hai cho integration test");
        GamificationRuntimeConfigRow approvedSecond = second;
        assertThatThrownBy(() -> runtimeConfigLifecycle.schedule(approvedSecond.getId(),
            approvedSecond.getVersion(), MAKER_ID,
            "Không được tồn tại revision scheduled thứ hai", effectiveAt))
            .isInstanceOf(DataAccessException.class);

        long waitMillis = Math.max(0L, effectiveAt.getTime() - System.currentTimeMillis() + 100L);
        Thread.sleep(waitMillis);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<GamificationRuntimeConfigRow>> futures = List.of(
                workers.submit(() -> activateTogether(ready, start)),
                workers.submit(() -> activateTogether(ready, start)));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            long winners = 0;
            for (Future<GamificationRuntimeConfigRow> future : futures) {
                if (future.get() != null) {
                    winners++;
                }
            }
            assertThat(winners).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM gamification_runtime_config WHERE status='ACTIVE'
                """, Integer.class)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM gamification_runtime_config WHERE status='SCHEDULED'
                """, Integer.class)).isZero();
        } finally {
            workers.shutdownNow();
        }
    }

    private GamificationRuntimeConfigRow activateTogether(CountDownLatch ready,
                                                           CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        return runtimeConfigLifecycle.activateDue(CHECKER_ID);
    }

    private GamificationPolicyBundleDetail createReadyDraft(String policyVersion) {
        GamificationPolicyBundleDetail draft = service.createDraft(policyVersion, "v1", MAKER_ID,
            "Tạo policy integration từ phiên bản đang phát hành");
        assertThat(draft.getLevels()).isNotEmpty();
        assertThat(draft.getQuests()).isNotEmpty();
        assertThat(draft.getRealms()).isNotEmpty();
        assertThat(draft.getPublicPolicy()).isNotNull();

        TicketRiskRuleRow rule = new TicketRiskRuleRow();
        rule.setRuleCode("DEVICE_VELOCITY_IT");
        rule.setMetricName("DEVICE_VOTES");
        rule.setThresholdValue(20L);
        rule.setWindowMinutes(60);
        rule.setScore(25);
        rule.setHardBlock(false);
        return service.saveAbuseRule(policyVersion, draft.getBundle().getVersion(), 50,
            rule, MAKER_ID, "Thêm rule chống lạm dụng cho integration test");
    }

    private String uniqueVersion(String prefix) {
        return "it-" + prefix + "-" + Long.toString(System.nanoTime(), 36);
    }
}
