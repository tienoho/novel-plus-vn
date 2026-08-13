package com.java2nb.novel.controller;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ReadingSubscriptionAdminPackagingTest {
    private final Path repository = Path.of("..").toAbsolutePath().normalize();

    @Test
    void migrationUiAndActivationFlagArePackagedFailClosed() throws Exception {
        String compose = read(repository.resolve("compose.yaml"));
        String env = read(repository.resolve(".env.example"));
        String runtime = read(repository.resolve("novel-admin/src/main/resources/application.yml"));
        String flywayImage = read(repository.resolve("deploy/flyway/Dockerfile"));
        String flywayEntrypoint = read(repository.resolve("deploy/flyway/entrypoint.sh"));
        String migration = read(repository.resolve(
            "doc/sql/20260801_reader_subscription_admin.sql"));
        String reviewMigration = read(repository.resolve(
            "doc/sql/20260805_reader_subscription_paid_review.sql"));
        String mandateAdminMigration = read(repository.resolve(
            "doc/sql/20260815_reading_subscription_mandate_admin.sql"));
        String template = read(repository.resolve(
            "novel-admin/src/main/resources/templates/novel/readingSubscription/readingSubscription.html"));
        String script = read(repository.resolve(
            "novel-admin/src/main/resources/static/js/appjs/novel/readingSubscription/readingSubscription.js"));

        assertThat(compose)
            .contains("dockerfile: deploy/flyway/Dockerfile")
            .contains("image: ${NOVEL_MIGRATIONS_IMAGE:-khoi-thu/migrations:${IMAGE_TAG:-local}}")
            .contains("condition: service_completed_successfully")
            .contains("READING_SUBSCRIPTION_ADMIN_ACTIVATION_ENABLED: "
                + "${READING_SUBSCRIPTION_ADMIN_ACTIVATION_ENABLED:-false}");
        assertThat(flywayImage)
            .contains("doc/sql/20260801_reader_subscription_admin.sql "
                + "/flyway/sql/V2026080101__reader_subscription_admin.sql")
            .contains("doc/sql/20260805_reader_subscription_paid_review.sql "
                + "/flyway/sql/V2026080501__reader_subscription_paid_review.sql")
            .contains("doc/sql/20260815_reading_subscription_mandate_admin.sql "
                + "/flyway/sql/V2026081501__reading_subscription_mandate_admin.sql");
        assertThat(flywayEntrypoint)
            .contains("flyway migrate")
            .contains("flyway validate");
        assertThat(env).contains("READING_SUBSCRIPTION_ADMIN_ACTIVATION_ENABLED=false");
        assertThat(runtime).contains(
            "activation-enabled: ${READING_SUBSCRIPTION_ADMIN_ACTIVATION_ENABLED:false}");
        assertThat(runtime.lines().filter("novel:"::equals).count()).isEqualTo(1);
        assertThat(migration)
            .contains("novel:readingSubscription:view")
            .contains("novel:readingSubscription:config")
            .contains("novel:readingSubscription:activate")
            .doesNotContain("INSERT INTO `reading_subscription_plan`");
        assertThat(reviewMigration)
            .contains("REFUND_PENDING")
            .contains("reading_subscription_purchase_review_audit")
            .contains("novel:readingSubscription:review")
            .contains("trg_rsp_review_audit_no_update")
            .contains("trg_rsp_review_audit_no_delete");
        assertThat(mandateAdminMigration)
            .contains("reading_subscription_mandate_admin_audit")
            .contains("RETRY_SCHEDULED")
            .contains("trg_rs_mandate_admin_audit_no_update")
            .contains("trg_rs_mandate_admin_audit_no_delete");
        assertThat(template)
            .contains("admin.subscription.title")
            .contains("admin.subscription.review.title")
            .contains("admin.subscription.mandate.title")
            .contains("mandateQueueTable")
            .contains("novel:readingSubscription:review");
        assertThat(script).doesNotContain("innerHTML");
    }

    private String read(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
