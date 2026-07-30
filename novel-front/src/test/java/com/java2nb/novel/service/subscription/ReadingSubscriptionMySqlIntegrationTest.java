package com.java2nb.novel.service.subscription;

import com.java2nb.novel.FrontNovelApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;

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
}
