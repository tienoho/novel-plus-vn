package com.java2nb.novel.i18n;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GiftCodePackagingTest {
    private final Path module = Path.of("").toAbsolutePath().normalize();
    private final Path repository = module.getParent();

    @Test
    void readerUiUsesRuntimeBaseForDesktopMobileAndAllThemePackages() throws Exception {
        String pageController = read(module.resolve(
            "src/main/java/com/java2nb/novel/controller/page/PageController.java"));
        String desktop = read(module.resolve(
            "src/main/resources/templates/user/gift_codes.html"));
        String mobile = read(module.resolve(
            "src/main/resources/templates/mobile/user/gift_codes.html"));
        String script = read(module.resolve(
            "src/main/resources/static/javascript/gift-code.js"));
        String css = read(module.resolve("src/main/resources/static/css/gift-code.css"));
        String historyDto = read(module.resolve(
            "src/main/java/com/java2nb/novel/dto/gift/GiftRedemptionHistoryItemResponse.java"));
        String adminPage = read(repository.resolve(
            "novel-admin/src/main/resources/templates/novel/giftCode/giftCode.html"));

        assertThat(pageController).contains(
            "@RequestMapping(\"user/gift_codes.html\")",
            "getUserDetails(request) == null",
            "ThreadLocalUtil.getTemplateDir() + \"user/gift_codes\"");
        assertThat(desktop).contains(
            "id=\"giftCodeApp\"", "id=\"giftCodeForm\"",
            "id=\"giftCodeHistoryList\"",
            "data-endpoint=\"/user/gift-codes/redeem\"",
            "/javascript/gift-code.js", "/css/gift-code.css");
        assertThat(mobile).contains(
            "id=\"giftCodeApp\"", "gift-code-shell-mobile",
            "/javascript/gift-code.js", "/css/gift-code.css");
        assertThat(script).contains(
            "credentials: 'same-origin'", "clientRequestId", "ALREADY_REDEEMED",
            "root.dataset.networkError", "root.dataset.historyEndpoint");
        assertThat(css).contains("@media (max-width: 767px)", ".gift-code-status.is-error");
        assertThat(historyDto).doesNotContain("userId", "clientRequestId", "campaignId", "codeId");
        assertThat(adminPage).contains(
            "id=\"giftCodeTable\"", "id=\"giftRedemptionTable\"",
            "id=\"giftCodeNext\"", "id=\"giftRedemptionNext\"");

        for (Path userCenter : new Path[] {
            module.resolve("src/main/resources/templates/user/userinfo.html"),
            module.resolve("src/main/resources/templates/mobile/user/userinfo.html"),
            repository.resolve("templates/green/html/user/userinfo.html"),
            repository.resolve("templates/orange/html/user/userinfo.html")
        }) {
            assertThat(read(userCenter)).contains("/user/gift_codes.html");
        }
    }

    @Test
    void giftMessagesHaveVietnameseAndChineseParity() throws Exception {
        Set<String> vietnamese = giftKeys(module.resolve(
            "src/main/resources/i18n/messages_vi_VN.properties"));
        Set<String> chinese = giftKeys(module.resolve(
            "src/main/resources/i18n/messages_zh_CN.properties"));

        assertThat(vietnamese).isNotEmpty().containsExactlyInAnyOrderElementsOf(chinese);
    }

    @Test
    void persistenceAndAdminLogsNeverStorePlaintextGiftCodes() throws Exception {
        String migration = read(repository.resolve("doc/sql/20260802_gift_codes.sql"));
        int tableStart = migration.indexOf("CREATE TABLE IF NOT EXISTS `gift_code`");
        int tableEnd = migration.indexOf("CREATE TABLE IF NOT EXISTS `gift_redemption`");
        String giftCodeTable = migration.substring(tableStart, tableEnd);
        String adminLogAspect = read(repository.resolve(
            "novel-admin/src/main/java/com/java2nb/common/aspect/WebLogAspect.java"));

        assertThat(giftCodeTable)
            .contains("`code_hash`", "`code_hint`")
            .doesNotContain("plaintext", "`code` ");
        assertThat(adminLogAspect)
            .contains("Không ghi payload phản hồi")
            .doesNotContain("logger.debug(\"Giá trị trả về: \" + ret)");
    }

    @Test
    void revokePermissionUsesASeparateIdempotentMigration() throws Exception {
        String migration = read(repository.resolve(
            "doc/sql/20260803_gift_code_revoke.sql"));
        String flywayImage = read(repository.resolve("deploy/flyway/Dockerfile"));

        assertThat(migration).contains(
            "novel:giftCode:revoke", "WHERE NOT EXISTS", "role.role_sign = 'admin'");
        assertThat(flywayImage).contains(
            "COPY --chmod=0444 doc/sql/20260803_gift_code_revoke.sql "
                + "/flyway/sql/V2026080301__gift_code_revoke.sql");
    }

    @Test
    void hmacRotationMigrationAndKeyRingConfigurationArePackaged() throws Exception {
        String migration = read(repository.resolve(
            "doc/sql/20260806_gift_code_hmac_rotation.sql"));
        String compose = read(repository.resolve("compose.yaml"));
        String flywayImage = read(repository.resolve("deploy/flyway/Dockerfile"));
        String runtime = read(module.resolve("src/main/resources/application.yml"));

        assertThat(migration)
            .contains("`hmac_key_id`")
            .contains("DEFAULT ''legacy-v1''")
            .contains("uk_gift_code_key_hash")
            .contains("trg_gift_code_hash_identity_no_update");
        assertThat(flywayImage).contains(
            "COPY --chmod=0444 doc/sql/20260806_gift_code_hmac_rotation.sql "
                + "/flyway/sql/V2026080601__gift_code_hmac_rotation.sql");
        assertThat(compose)
            .contains("GIFT_CODE_HMAC_KEY_ID")
            .contains("GIFT_CODE_HMAC_VERIFICATION_KEYS");
        assertThat(runtime)
            .contains("hmac-key-id: ${GIFT_CODE_HMAC_KEY_ID:legacy-v1}")
            .contains("hmac-verification-keys: ${GIFT_CODE_HMAC_VERIFICATION_KEYS:}");
    }

    private Set<String> giftKeys(Path properties) throws Exception {
        Set<String> keys = new LinkedHashSet<>();
        for (String line : Files.readAllLines(properties, StandardCharsets.UTF_8)) {
            if (line.startsWith("gift.")) {
                keys.add(line.substring(0, line.indexOf('=')));
            }
        }
        return keys;
    }

    private String read(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
