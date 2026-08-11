package com.java2nb.novel.i18n;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionDeploymentPackagingTest {

    private final Path repository = Path.of("..").toAbsolutePath().normalize();

    @Test
    void allExternalWorkflowActionsArePinnedToCommitSha() throws Exception {
        List<Path> workflows;
        try (Stream<Path> files = Files.list(repository.resolve(".github/workflows"))) {
            workflows = files.filter(path -> path.getFileName().toString().endsWith(".yml")).toList();
        }
        Pattern action = Pattern.compile("(?m)^\\s*uses:\\s*([^#\\s]+)");
        for (Path workflow : workflows) {
            Matcher matcher = action.matcher(Files.readString(workflow, StandardCharsets.UTF_8));
            while (matcher.find()) {
                String reference = matcher.group(1);
                if (!reference.startsWith("./")) {
                    assertThat(reference)
                        .as("Action phải pin SHA 40 ký tự trong %s", workflow.getFileName())
                        .matches(".+@[0-9a-f]{40}");
                }
            }
        }
    }

    @Test
    void productionComposePublishesOnlyCaddyAndUsesFileSecrets() throws Exception {
        String compose = read("compose.yaml");
        String testOverride = read("compose.test.yaml");

        assertThat(compose)
            .contains("redis:7-alpine@sha256:e7723ff73d963f5cc6d9c4643ea3d989527a402a319239054e9472a7fb9219a2")
            .contains("quay.io/prometheus/prometheus:v3.13.2@sha256:508729e0e2d18e11fd742a5a5ca70e557b940a93948c3c95fd0123a6fd538b69")
            .contains("${NOVEL_GRAFANA_IMAGE:-novel-plus/grafana:${IMAGE_TAG:-local}}")
            .contains("dockerfile: deploy/caddy/Dockerfile")
            .contains("- \"${CADDY_HTTP_PORT:-80}:80\"",
                "- \"${CADDY_HTTPS_PORT:-443}:443\"",
                "- \"${CADDY_HTTPS_PORT:-443}:443/udp\"")
            .contains("DB_PASSWORD_FILE: /run/secrets/mysql_app_password")
            .contains("JWT_SECRET_FILE: /run/secrets/jwt_secret")
            .contains("VIETQR_WEBHOOK_SECRET_FILE: /run/secrets/vietqr_webhook_secret")
            .contains("${NOVEL_FRONT_IMAGE:-novel-plus/front:${IMAGE_TAG:-local}}")
            .contains("${NOVEL_ADMIN_IMAGE:-novel-plus/admin:${IMAGE_TAG:-local}}")
            .contains("${NOVEL_CRAWL_IMAGE:-novel-plus/crawl:${IMAGE_TAG:-local}}")
            .contains("${NOVEL_MIGRATIONS_IMAGE:-novel-plus/migrations:${IMAGE_TAG:-local}}")
            .contains("${NOVEL_CADDY_IMAGE:-novel-plus/caddy:${IMAGE_TAG:-local}}")
            .contains("${NOVEL_BACKUP_IMAGE:-novel-plus/backup:${IMAGE_TAG:-local}}")
            .contains("${NOVEL_ALERTMANAGER_IMAGE:-novel-plus/alertmanager:${IMAGE_TAG:-local}}")
            .contains("${NOVEL_PUSHGATEWAY_IMAGE:-novel-plus/pushgateway:${IMAGE_TAG:-local}}")
            .contains("${NOVEL_MYSQL_IMAGE:-novel-plus/mysql:${IMAGE_TAG:-local}}")
            .contains("dockerfile: deploy/observability/pushgateway/Dockerfile")
            .contains("dockerfile: deploy/observability/grafana/Dockerfile")
            .contains("dockerfile: deploy/mysql/Dockerfile")
            .contains("--persistence.file=/pushgateway/metrics.db")
            .contains("pushgateway-data:/pushgateway")
            .contains("condition: service_completed_successfully")
            .doesNotContain("${FRONT_PORT", "${ADMIN_PORT", "${CRAWL_PORT")
            .doesNotContain("127.0.0.1:${MYSQL_HOST_PORT:-3307}:3306");
        compose.lines()
            .map(String::trim)
            .filter(line -> line.startsWith("image:"))
            .filter(line -> !line.contains("${NOVEL_"))
            .forEach(line -> assertThat(line)
                .as("Dependency image production phải pin digest: %s", line)
                .matches("image: .+@sha256:[0-9a-f]{64}"));
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
        assertThat(read("deploy/backup/Dockerfile"))
            .contains("FROM docker.io/library/mysql:8.4.11@sha256:b3b90af2a6552ae30c266fdb7d5dd55f3afb72404bb78d37fe8a23eb857fd3fb");
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
        String ci = read(".github/workflows/ci.yml");
        assertThat(ci)
            .contains("mvn -B -ntp clean verify -Pcentral-repo")
            .contains("github/codeql-action/init@")
            .contains("gitleaks/gitleaks-action@")
            .contains("actions/dependency-review-action@")
            .contains("aquasecurity/trivy-action@57a97c7e7821a5776cebc9bb87c984fa69cba8f1")
            .contains("version: v0.69.3")
            .contains("quay.io/prometheus/prometheus:v3.13.2@sha256:508729e0e2d18e11fd742a5a5ca70e557b940a93948c3c95fd0123a6fd538b69")
            .contains("anchore/sbom-action@")
            .contains("syft-version: '1.50.0'")
            .contains("node --test scripts/generate-release-manifest.test.mjs")
            .contains("deploy/caddy/Dockerfile", "deploy/backup/Dockerfile")
            .contains("deploy/observability/pushgateway/Dockerfile")
            .contains("deploy/observability/grafana/Dockerfile")
            .contains("deploy/mysql/Dockerfile");
        assertThat(ci).doesNotContain("a9c7b0f06e461e9d4b4d1711f154ee024b8d7ab8");
        assertPinnedGitleaksPositiveControl(ci);
    }

    @Test
    void releaseBuildsScansAndPushesTheSameImageOnce() throws Exception {
        String release = read(".github/workflows/release.yml");

        assertThat(release)
            .containsOnlyOnce("uses: docker/build-push-action@")
            .contains("aquasecurity/trivy-action@57a97c7e7821a5776cebc9bb87c984fa69cba8f1")
            .contains("version: v0.69.3")
            .contains("runtime-dependencies:")
            .contains("needs: [verify, secret-scan, runtime-dependencies]")
            .contains("docker.io/library/redis:7-alpine@sha256:e7723ff73d963f5cc6d9c4643ea3d989527a402a319239054e9472a7fb9219a2")
            .contains("quay.io/prometheus/prometheus:v3.13.2@sha256:508729e0e2d18e11fd742a5a5ca70e557b940a93948c3c95fd0123a6fd538b69")
            .doesNotContain("name: grafana\n            image: 'docker.io/grafana/grafana")
            .contains("subject-path: trivy-runtime-${{ matrix.name }}.json")
            .contains("node scripts/generate-release-manifest.mjs verify-runtime-reports")
            .contains("runtime-dependency-evidence/*")
            .contains("load: true", "push: false")
            .contains("SCANNED_IMAGE: novel-plus/release-${{ matrix.image }}:scan")
            .contains("tag_id=\"$(docker image inspect")
            .contains("push_output=\"$(docker push")
            .contains("tag_digest=\"$(sed -nE")
            .contains("Các tag phát hành không cùng digest")
            .contains("uses: actions/attest@")
            .contains("sbom-path: sbom-release-${{ matrix.image }}.spdx.json")
            .contains("syft-version: '1.50.0'")
            .contains("if: startsWith(github.ref, 'refs/tags/v')")
            .contains("attestation-${{ matrix.image }}.json")
            .contains("release-manifest:")
            .contains("node scripts/generate-release-manifest.mjs create")
            .contains("node scripts/generate-release-manifest.mjs verify")
            .contains("release-smoke:")
            .contains("node scripts/generate-release-manifest.mjs candidate-env")
            .contains("-SkipImageBuild")
            .contains("-VerifyBackupRestore")
            .contains("rc-compose-smoke.json")
            .contains("backupRestoreVerified = $true")
            .contains("subject-path: rc-compose-smoke.json")
            .contains("needs: [release-manifest, release-smoke]")
            .contains("release-runtime-smoke/**")
            .contains("draft: true", "make_latest: false")
            .contains("image: novel-caddy", "image: novel-backup", "image: novel-alertmanager")
            .contains("image: novel-pushgateway")
            .contains("image: novel-mysql")
            .contains("image: novel-grafana")
            .doesNotContain("type=raw,value=latest");
        assertThat(release).doesNotContain("a9c7b0f06e461e9d4b4d1711f154ee024b8d7ab8");
        assertThat(read("scripts/generate-release-manifest.mjs"))
            .contains("novel-front", "novel-admin", "novel-crawl", "novel-migrations")
            .contains("novel-caddy", "novel-backup", "novel-alertmanager", "novel-pushgateway", "novel-mysql", "novel-grafana")
            .contains("SBOM không có quan hệ DESCRIBES")
            .contains("Release manifest không khớp bộ artifact hiện tại")
            .contains("Tag release phải bắt đầu bằng v")
            .contains("Checksum release manifest không hợp lệ")
            .contains("createCandidateEnv")
            .contains("verifyCandidateSmokeRecord")
            .contains("verifyRuntimeDependencyReports")
            .contains("Dependency runtime còn finding ${field}")
            .contains("chưa xác minh backup và restore drill")
            .contains("createDeploymentEnv")
            .contains("Promotion record không khớp hash release manifest")
            .contains("NOVEL_FRONT_IMAGE", "NOVEL_MIGRATIONS_IMAGE")
            .contains("NOVEL_CADDY_IMAGE", "NOVEL_BACKUP_IMAGE", "NOVEL_ALERTMANAGER_IMAGE")
            .contains("NOVEL_PUSHGATEWAY_IMAGE", "NOVEL_MYSQL_IMAGE", "NOVEL_GRAFANA_IMAGE");
        assertThat(read("scripts/generate-release-manifest.test.mjs"))
            .contains("tạo và xác minh manifest gắn đúng mười image với RC")
            .contains("CLI tạo rồi xác minh manifest theo đúng lệnh workflow")
            .contains("sinh Compose env cho smoke RC từ manifest đã xác minh")
            .contains("từ chối candidate-env khi commit không khớp manifest")
            .contains("xác minh biên bản smoke RC khớp tag commit và manifest")
            .contains("từ chối biên bản smoke RC không khớp hash manifest")
            .contains("từ chối biên bản smoke RC chưa xác minh backup restore")
            .contains("xác minh đủ hai report dependency runtime sạch theo digest")
            .contains("từ chối dependency runtime còn finding")
            .contains("từ chối report dependency dùng ArtifactName không đúng dạng Trivy theo digest")
            .contains("từ chối artifact bị sửa sau khi tạo manifest")
            .contains("từ chối bộ artifact thiếu một image")
            .contains("từ chối tag không phải release candidate")
            .contains("sinh Compose env chỉ từ manifest và promotion record khớp nhau")
            .contains("từ chối deploy-env khi promotion record không khớp manifest");
        assertThat(read("scripts/smoke-admin-compose.ps1"))
            .contains("[switch]$SkipImageBuild")
            .contains("[switch]$VerifyBackupRestore")
            .contains("[string]$ReleaseEnvFile")
            .contains("$composeGlobalArguments")
            .contains("$upArguments += \"--no-build\"")
            .contains("Invoke-Compose @(\"run\", \"--rm\", \"backup\")")
            .contains("Invoke-Compose @(\"run\", \"--rm\", \"restore-drill\")");
        assertThat(read(".gitignore")).contains("/.env.release", "/.env.rc-smoke");
        assertThat(release.indexOf("Build một lần image phát hành"))
            .isLessThan(release.indexOf("Quét CVE, secret và cấu hình image"));
        assertThat(release.indexOf("Quét CVE, secret và cấu hình image"))
            .isLessThan(release.indexOf("Đẩy đúng image đã kiểm tra"));
        assertPinnedGitleaksPositiveControl(release);
    }

    @Test
    void productionPromotionRequiresApprovalAndVerifiesReleaseEvidence() throws Exception {
        String promotion = read(".github/workflows/promote-production.yml");

        assertThat(promotion)
            .contains("environment:", "name: production")
            .contains("approval_record_id:", "approval_record_sha256:")
            .contains("^v[0-9][0-9A-Za-z._-]*$")
            .contains("^[0-9a-f]{64}$")
            .contains("gh release view \"$RELEASE_TAG\"")
            .contains("rc-compose-smoke.json")
            .contains("gh attestation verify release-meta/rc-compose-smoke.json")
            .contains("generate-release-manifest.mjs verify-smoke-record")
            .contains("node scripts/generate-release-manifest.mjs verify")
            .contains("--pattern 'trivy-novel-*'")
            .contains("--pattern 'trivy-runtime-*'")
            .contains("generate-release-manifest.mjs verify-runtime-reports")
            .contains("gh attestation verify \"$report\"")
            .contains("gh attestation verify \"$image\"")
            .contains("--predicate-type https://spdx.dev/Document/v2.3")
            .contains("production-promotion-record.json")
            .contains("--clobber")
            .contains("manifest.images.length !== 10")
            .contains("--draft=false", "--latest", "--verify-tag");
    }

    @Test
    void alertsWhenMandateRevocationQueueRemainsPending() throws Exception {
        assertThat(read("deploy/observability/rules/novel-plus-alerts.yml"))
            .contains("VnpayRecurringMandateRevocationPending")
            .contains("novel_subscription_renewal_queue{state=\"mandate_revoke_pending\"} > 0")
            .contains("for: 15m");
    }

    @Test
    void alertmanagerBinariesAreRebuiltFromPinnedAndPatchedSources() throws Exception {
        assertThat(read("deploy/observability/alertmanager/Dockerfile"))
            .contains("FROM golang:1.26.5-alpine@sha256:0178a641fbb4858c5f1b48e34bdaabe0350a330a1b1149aabd498d0699ff5fb2 AS builder")
            .contains("FROM quay.io/prometheus/alertmanager:v${ALERTMANAGER_VERSION}@sha256:9e082985f56f4c8c9f724e18f2288c6708f472e56a5286b8863d080434ea065d")
            .contains("ALERTMANAGER_VERSION=0.33.1")
            .contains("ALERTMANAGER_COMMIT=2c8da51e03f3dbbed24f9711ca2d76aab4eef9c5")
            .contains("ALERTMANAGER_UI_SHA256=1f63344e196e47ba7bfe27276f44c1da77e39fb76493e42b2cf0a50ca8f04321")
            .contains("golang.org/x/crypto@v0.53.0")
            .contains("golang.org/x/text@v0.39.0")
            .contains("google.golang.org/grpc@v1.82.1")
            .contains("github.com/prometheus/common/version.Version=${ALERTMANAGER_VERSION}")
            .contains("COPY --from=builder --chmod=0555 /out/alertmanager /bin/alertmanager")
            .contains("COPY --from=builder --chmod=0555 /out/amtool /bin/amtool");
    }

    @Test
    void pushgatewayBinaryIsRebuiltFromPinnedAndPatchedSource() throws Exception {
        assertThat(read("deploy/observability/pushgateway/Dockerfile"))
            .contains("FROM golang:1.26.5-alpine@sha256:0178a641fbb4858c5f1b48e34bdaabe0350a330a1b1149aabd498d0699ff5fb2 AS builder")
            .contains("PUSHGATEWAY_VERSION=1.11.3")
            .contains("PUSHGATEWAY_COMMIT=e803ebd81be5867ff17a21205030611fa033af13")
            .contains("golang.org/x/sync@v0.21.0", "golang.org/x/text@v0.39.0")
            .contains("go test ./...")
            .contains("FROM docker.io/prom/pushgateway:v${PUSHGATEWAY_VERSION}@sha256:74fa117cef2d7e383112d25139ff1c2d2e309c35389a9e0554a47136a1482e48")
            .contains("COPY --from=builder --chown=65534:65534 --chmod=0555 /out/pushgateway /bin/pushgateway");
    }

    @Test
    void grafanaExcludesUnusedVulnerableDataSourcesAndRebuildsItsBackend() throws Exception {
        assertThat(read("deploy/observability/grafana/Dockerfile"))
            .contains("FROM docker.io/library/golang:1.26.5-bookworm@sha256:6c5605ab3a9a9fb3c4eafe5b3d63cdbf3881caf113262b67862547b54a9db599 AS builder")
            .contains("GRAFANA_VERSION=13.1.3")
            .contains("GRAFANA_COMMIT=45a27d64b64a82d666b06aa5c5bb3521587edb0d")
            .contains("GRAFANA_GO_BUILD_PARALLELISM=2")
            .contains("GOMAXPROCS=${GRAFANA_GO_BUILD_PARALLELISM}")
            .contains("GOFLAGS=-p=${GRAFANA_GO_BUILD_PARALLELISM}")
            .contains("test \"$(git -C /src rev-parse HEAD)\" = \"${GRAFANA_COMMIT}\"")
            .contains("git apply --check /tmp/grafana-no-tempo.patch")
            .contains("gen -tags oss -gen_tags '(!enterprise && !pro)' ./pkg/server")
            .contains("! grep -q 'github.com/grafana/tempo'")
            .contains("FROM docker.io/grafana/grafana:13.1.3@sha256:ab5cb380e3ff3172d6c8bd2e7cfd31cce977d2881b260e1f5bc089bf0b759b43")
            .contains("/usr/share/grafana/public/app/plugins/datasource/tempo")
            .contains("/usr/share/grafana/data/plugins-bundled/elasticsearch")
            .contains("/usr/share/grafana/data/plugins-bundled/zipkin")
            .contains("GF_PLUGINS_PREINSTALL_DISABLED=true")
            .contains("GF_PLUGINS_DISABLE_PLUGINS=tempo,elasticsearch,zipkin")
            .contains("ENTRYPOINT [\"/usr/local/bin/novel-grafana-entrypoint\"]")
            .contains("USER 472");
        assertThat(read("deploy/observability/grafana/entrypoint.sh"))
            .contains("rm -rf -- \"${GF_PATHS_PLUGINS:?}/${plugin_id}\"")
            .contains("exec /run.sh \"$@\"");
        assertThat(read("deploy/observability/grafana/grafana-no-tempo.patch"))
            .contains("github.com/grafana/grafana/pkg/tsdb/tempo")
            .contains("tempo.ProvideService")
            .contains("Tempo:           asBackendPlugin(t)");
    }

    @Test
    void mysqlUsesPatchedGosuAndExcludesUnusedMysqlShell() throws Exception {
        assertThat(read("deploy/mysql/Dockerfile"))
            .contains("FROM golang:1.26.5-alpine@sha256:0178a641fbb4858c5f1b48e34bdaabe0350a330a1b1149aabd498d0699ff5fb2 AS gosu-builder")
            .contains("GOSU_VERSION=1.19")
            .contains("GOSU_COMMIT=6456aaa0f3c854d199d0f037f068eb97515b7513")
            .contains("golang.org/x/sys@v0.45.0")
            .contains("go test ./...")
            .contains("FROM docker.io/library/mysql:8.4.11@sha256:b3b90af2a6552ae30c266fdb7d5dd55f3afb72404bb78d37fe8a23eb857fd3fb")
            .contains("rm -rf /usr/lib/mysqlsh /usr/libexec/mysqlsh /usr/share/mysqlsh")
            .contains("COPY --from=gosu-builder --chmod=0555 /out/gosu /usr/local/bin/gosu")
            .contains("test \"$(gosu mysql id -un)\" = mysql");
    }

    @Test
    void vnpayUatHarnessIsSelfContainedAndRequiresProviderDeliveredIpn() throws Exception {
        assertThat(read("scripts/prepare-vnpay-uat.ps1"))
            .contains("VNPAY_SANDBOX_HASH_SECRET_FILE")
            .contains("vnpay_sandbox_card_number", "vnpay_sandbox_otp")
            .contains("https://$Domain/pay/vnpay/ipn")
            .contains("VNPAY_RECURRING_ENABLED=false");
        assertThat(read("scripts/test-vnpay-uat.ps1"))
            .contains("node:22-alpine")
            .contains("mcr.microsoft.com/playwright:v1.62.1-noble")
            .contains("VNPAY_SMOKE_IPN_MODE = 'provider'")
            .contains("Get-SandboxTestValue")
            .contains("providerServerDeliveryVerified")
            .contains("ledger zero-sum");
        assertThat(read("scripts/preflight-vnpay-checkout.ps1"))
            .contains("VNPAY_SMOKE_MODE = 'checkout-preflight'")
            .contains("vnpay-registration-preflight.json")
            .contains("checkoutUrlVerified", "tmnCodeVerified", "returnUrlVerified");
        assertThat(read("scripts/start-vnpay-uat.ps1"))
            .contains("preflight-vnpay-checkout.ps1");
        assertThat(read("scripts/vnpay-sandbox-smoke.mjs"))
            .contains("paymentUrl.searchParams.get('vnp_TmnCode') !== expectedTmnCode")
            .contains("paymentUrl.searchParams.get('vnp_ReturnUrl')")
            .contains("stage: 'CHECKOUT_PREFLIGHT'");
        assertThat(read("scripts/run-vnpay-sandbox-smoke.ps1"))
            .contains("[switch]$CheckoutPreflightOnly")
            .contains("pay_status=2")
            .contains("Assertion checkout-preflight VNPAY Sandbox thất bại.");
        assertThat(read(".gitignore"))
            .contains("/.env.uat", "/.vnpay-uat-registration.txt", "/secrets/*");
    }

    @Test
    void mandateIdempotencyMigrationIsPackagedAfterRevocationMigrations() throws Exception {
        String dockerfile = read("deploy/flyway/Dockerfile");
        assertThat(dockerfile)
            .contains("V2026081401__reading_subscription_mandate_revocation.sql")
            .contains("V2026081501__reading_subscription_mandate_admin.sql")
            .contains("V2026081601__reading_subscription_mandate_idempotency.sql");
        assertThat(read("doc/sql/20260816_reading_subscription_mandate_idempotency.sql"))
            .contains("uk_rs_mandate_client_request")
            .contains("provider_data_key_ciphertext")
            .contains("trg_rs_mandate_request_snapshot_no_update");
    }

    @Test
    void authorPayoutFourEyesAndGamificationConfigMigrationsArePackagedInOrder() throws Exception {
        assertThat(read("deploy/flyway/Dockerfile"))
            .containsSubsequence(
                "V2026081701__author_payout_four_eyes.sql",
                "V2026081801__gamification_runtime_config.sql",
                "V2026081901__gamification_dynamic_config_p1_hardening.sql");
        assertThat(read("doc/sql/20260817_author_payout_four_eyes.sql"))
            .contains("approved_by")
            .contains("executed_by")
            .contains("chk_author_withdrawal_four_eyes")
            .contains("novel:authorFinance:payout:approve")
            .contains("novel:authorFinance:payout:execute");
    }

    @Test
    void e2eRunnerAlwaysRemovesBrowserCredentialState() throws Exception {
        assertThat(read("compose.e2e.yaml"))
            .contains("depends_on: !override")
            .contains("front:", "admin:", "crawl:");
        assertThat(read("scripts/run-e2e.ps1"))
            .contains("@(\"front\", \"crawl\", \"admin\", \"caddy\")")
            .doesNotContain("@(\"front\", \"crawl\", \"admin\", \"alertmanager\"");
        assertThat(read("scripts/run-e2e.ps1"))
            .contains("$authDir = Join-Path $e2eRoot \".auth\"")
            .contains("$resolvedAuth.StartsWith($resolvedE2e")
            .contains("if (Test-Path -LiteralPath $resolvedAuth)")
            .contains("Remove-Item -LiteralPath $resolvedAuth -Recurse -Force");
        assertThat(read(".gitignore")).contains("e2e/.auth/");
        assertThat(read(".dockerignore")).contains("e2e/.auth", "tmp");
    }

    private String read(String relativePath) throws Exception {
        return Files.readString(repository.resolve(relativePath), StandardCharsets.UTF_8)
            .replace("\r\n", "\n");
    }

    private void assertPinnedGitleaksPositiveControl(String workflow) {
        assertThat(workflow)
            .contains("GITLEAKS_VERSION: '8.30.0'")
            .contains("zricethezav/gitleaks:v8.30.0@sha256:691af3c7c5a48b16f187ce3446d5f194838f91238f27270ed36eef6359a574d9")
            .contains("stdin --no-banner --redact --exit-code 17")
            .contains("Gitleaks positive control không phát hiện fixture tổng hợp");
    }
}
