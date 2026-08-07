package com.java2nb.novel.i18n;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionDeploymentPackagingTest {

    private final Path repository = Path.of("..").toAbsolutePath().normalize();

    @Test
    void productionComposePublishesOnlyCaddyAndUsesFileSecrets() throws Exception {
        String compose = read("compose.yaml");
        String testOverride = read("compose.test.yaml");

        assertThat(compose)
            .contains("dockerfile: deploy/caddy/Dockerfile")
            .contains("- \"${CADDY_HTTP_PORT:-80}:80\"",
                "- \"${CADDY_HTTPS_PORT:-443}:443\"",
                "- \"${CADDY_HTTPS_PORT:-443}:443/udp\"")
            .contains("DB_PASSWORD_FILE: /run/secrets/mysql_app_password")
            .contains("JWT_SECRET_FILE: /run/secrets/jwt_secret")
            .contains("VIETQR_WEBHOOK_SECRET_FILE: /run/secrets/vietqr_webhook_secret")
            .contains("condition: service_completed_successfully")
            .doesNotContain("${FRONT_PORT", "${ADMIN_PORT", "${CRAWL_PORT")
            .doesNotContain("127.0.0.1:${MYSQL_HOST_PORT:-3307}:3306");
        assertThat(testOverride).contains("127.0.0.1:${MYSQL_HOST_PORT:-3307}:3306");
    }

    @Test
    void caddyEnforcesTlsHeadersUploadAndPinnedRateLimitModule() throws Exception {
        assertThat(read("deploy/caddy/Dockerfile"))
            .contains("ARG CADDY_VERSION=2.11.4")
            .contains("FROM caddy:${CADDY_VERSION}-builder-alpine AS builder")
            .contains("FROM caddy:${CADDY_VERSION}-alpine")
            .contains("github.com/mholt/caddy-ratelimit@5625512f24f6f59d6f64fb3aafe5eecff0b286db");
        assertThat(read("deploy/caddy/Caddyfile"))
            .contains("request_body", "max_size {$CADDY_MAX_UPLOAD_SIZE:25MB}")
            .contains("rate_limit", "method POST PUT PATCH DELETE", "ipv6_prefix 64")
            .contains("Strict-Transport-Security", "X-Content-Type-Options")
            .contains("reverse_proxy front:8083", "reverse_proxy admin:80", "reverse_proxy crawl:8081");
    }

    @Test
    void encryptedBackupRestoreAndDrillArePackaged() throws Exception {
        assertThat(read("deploy/backup/backup.sh"))
            .contains("mysqldump")
            .contains("--single-transaction --routines --triggers --events --hex-blob")
            .contains("sha256sum database.sql files.tar.gz")
            .contains("--symmetric --cipher-algo AES256");
        assertThat(read("deploy/backup/restore.sh"))
            .contains("RESTORE_CONFIRM", "RESTORE_DRILL")
            .contains("sha256sum -c SHA256SUMS")
            .contains("information_schema.tables")
            .contains("DROP DATABASE IF EXISTS");
        assertThat(read("compose.yaml"))
            .contains("profiles: [\"tools\"]")
            .contains("entrypoint: [\"/usr/local/bin/novel-backup\"]")
            .contains("entrypoint: [\"/usr/local/bin/novel-restore\"]")
            .contains("backup-work:/work");
        assertThat(read("scripts/verify-backup-restore.ps1"))
            .contains("'novel-plus-backup-verify'")
            .contains("@('run', '--rm', 'backup')")
            .contains("@('run', '--rm', 'restore-drill')")
            .contains("@('down', '--volumes', '--remove-orphans')");
        assertThat(read(".github/workflows/gamification-mysql.yml"))
            .contains("./scripts/verify-backup-restore.ps1");
    }

    @Test
    void pullRequestWorkflowRunsSecurityAndImageGates() throws Exception {
        assertThat(read(".github/workflows/ci.yml"))
            .contains("mvn -B -ntp clean verify -Pcentral-repo")
            .contains("github/codeql-action/init@")
            .contains("gitleaks/gitleaks-action@")
            .contains("actions/dependency-review-action@")
            .contains("aquasecurity/trivy-action@")
            .contains("anchore/sbom-action@")
            .contains("deploy/caddy/Dockerfile", "deploy/backup/Dockerfile");
    }

    private String read(String relativePath) throws Exception {
        return Files.readString(repository.resolve(relativePath), StandardCharsets.UTF_8)
            .replace("\r\n", "\n");
    }
}
