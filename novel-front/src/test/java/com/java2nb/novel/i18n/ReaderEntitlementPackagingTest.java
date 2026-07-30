package com.java2nb.novel.i18n;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ReaderEntitlementPackagingTest {

    private final Path repository = Path.of("..").toAbsolutePath().normalize();

    @Test
    void migrationIsMountedAndRuntimeDefaultsStayDisabled() throws Exception {
        String compose = read(repository.resolve("compose.yaml"));
        String runtime = read(repository.resolve("novel-front/src/main/resources/application.yml"));
        String distribution = read(
            repository.resolve("novel-front/src/main/build/config/application.yml"));
        String envExample = read(repository.resolve(".env.example"));

        assertThat(compose)
            .contains("/migrations/20260730_reader_entitlements.sql")
            .contains("/migrations/20260731_reader_subscriptions.sql")
            .contains("/migrations/20260804_reader_subscription_checkout.sql")
            .contains("./doc/sql/20260730_reader_entitlements.sql:"
                + "/migrations/20260730_reader_entitlements.sql:ro")
            .contains("./doc/sql/20260731_reader_subscriptions.sql:"
                + "/migrations/20260731_reader_subscriptions.sql:ro")
            .contains("./doc/sql/20260804_reader_subscription_checkout.sql:"
                + "/migrations/20260804_reader_subscription_checkout.sql:ro")
            .contains("READING_TICKET_ENABLED: ${READING_TICKET_ENABLED:-false}");
        assertThat(runtime)
            .contains("enabled: ${READING_TICKET_ENABLED:false}")
            .contains("subscription-grant-cron: ${READING_SUBSCRIPTION_GRANT_CRON:")
            .contains("subscription-zone-id: ${READING_SUBSCRIPTION_ZONE_ID:Asia/Ho_Chi_Minh}")
            .contains("subscription-grant-batch-size: ${READING_SUBSCRIPTION_GRANT_BATCH_SIZE:200}");
        assertThat(distribution)
            .contains("enabled: ${READING_TICKET_ENABLED:false}")
            .contains("subscription-grant-cron: ${READING_SUBSCRIPTION_GRANT_CRON:")
            .contains("subscription-zone-id: ${READING_SUBSCRIPTION_ZONE_ID:Asia/Ho_Chi_Minh}")
            .contains("subscription-grant-batch-size: ${READING_SUBSCRIPTION_GRANT_BATCH_SIZE:200}");
        assertThat(envExample)
            .contains("READING_TICKET_ENABLED=false")
            .contains("READING_SUBSCRIPTION_GRANT_CRON=")
            .contains("READING_SUBSCRIPTION_ZONE_ID=Asia/Ho_Chi_Minh")
            .contains("READING_SUBSCRIPTION_GRANT_BATCH_SIZE=200");
    }

    @Test
    void schemaAndApiKeepReadingTicketsSeparateFromMonthlyTorches() throws Exception {
        String migration = read(repository.resolve("doc/sql/20260730_reader_entitlements.sql"));
        String mapper = read(repository.resolve(
            "novel-common/src/main/resources/mybatis/mapping/ReadingTicketMapper.xml"));
        String controller = read(repository.resolve(
            "novel-front/src/main/java/com/java2nb/novel/controller/ReadingTicketController.java"));
        String checkoutMigration = read(repository.resolve(
            "doc/sql/20260804_reader_subscription_checkout.sql"));

        assertThat(migration)
            .contains("CREATE TABLE IF NOT EXISTS `reading_ticket_account`")
            .contains("CREATE TABLE IF NOT EXISTS `reading_ticket_ledger`")
            .contains("CREATE TABLE IF NOT EXISTS `chapter_entitlement`")
            .contains("UNIQUE KEY `uk_chapter_entitlement_active`")
            .contains("trg_reading_ticket_ledger_no_update");
        assertThat(mapper)
            .contains("FROM reading_ticket_account")
            .contains("FROM chapter_entitlement")
            .doesNotContain("monthly_ticket_account");
        assertThat(controller)
            .contains("@GetMapping(\"user/reading-tickets\")")
            .contains("reading-ticket-unlock")
            .contains("READING_TICKET_DISABLED");
        assertThat(checkoutMigration)
            .contains("CREATE TABLE IF NOT EXISTS `reading_subscription_purchase`")
            .contains("UNIQUE KEY `uk_rsp_user_request`")
            .contains("UNIQUE KEY `uk_rsp_one_open_user`")
            .contains("'PENDING', 'ACTIVATED', 'PAID_REVIEW', 'FAILED'")
            .contains("trg_rsp_snapshot_no_update");
    }

    @Test
    void subscriptionSchemaAndMapperPreservePeriodGrantInvariants() throws Exception {
        String migration = read(repository.resolve("doc/sql/20260731_reader_subscriptions.sql"));
        String mapper = read(repository.resolve(
            "novel-common/src/main/resources/mybatis/mapping/ReadingSubscriptionMapper.xml"));

        assertThat(migration)
            .contains("CREATE TABLE IF NOT EXISTS `reading_subscription_plan`")
            .contains("CREATE TABLE IF NOT EXISTS `user_reading_subscription`")
            .contains("CREATE TABLE IF NOT EXISTS `reading_subscription_period_grant`")
            .contains("UNIQUE KEY `uk_rs_subscription_source`")
            .contains("UNIQUE KEY `uk_rs_period_subscription_start`")
            .contains("trg_rs_period_grant_no_update")
            .doesNotContain("DROP TABLE");
        assertThat(mapper)
            .contains("selectDueSubscriptionIds")
            .contains("selectSubscriptionBySourceForUpdate")
            .contains("LIMIT 1 FOR UPDATE")
            .contains("version=#{expectedVersion}");
    }

    @Test
    void subscriptionPublicControllerKeepsCheckoutSessionOwned() throws Exception {
        String controller = read(repository.resolve(
            "novel-front/src/main/java/com/java2nb/novel/controller/ReadingSubscriptionController.java"));

        assertThat(controller)
            .contains("@GetMapping(\"plans\")")
            .contains("@GetMapping(\"current\")")
            .contains("@GetMapping(\"{subscriptionId}/period-grants\")")
            .contains("@PostMapping(\"checkouts\")")
            .contains("createSubscriptionCheckout(")
            .contains("requireUser(request).getId()")
            .doesNotContain("@PutMapping")
            .doesNotContain("activate(");
    }

    private String read(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
