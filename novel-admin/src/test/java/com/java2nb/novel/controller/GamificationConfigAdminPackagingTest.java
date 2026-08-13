package com.java2nb.novel.controller;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.junit.jupiter.api.Test;
import org.springframework.ui.Model;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GamificationConfigAdminPackagingTest {

    private final Path module = Path.of("").toAbsolutePath().normalize();

    @Test
    void pageAndApiPermissionsAreSeparatedByLifecycleAction() throws Exception {
        assertPermission(GamificationAdminController.class, "settings",
            "novel:gamification:settings:view", Model.class);
        assertPermission(GamificationAdminController.class, "policies",
            "novel:gamification:policy:view", Model.class);

        assertPermission(GamificationRuntimeSettingsController.class, "active",
            "novel:gamification:settings:view");
        assertPermission(GamificationRuntimeSettingsController.class, "cloneActive",
            "novel:gamification:settings:edit",
            GamificationRuntimeSettingsController.ReasonRequest.class);
        assertPermission(GamificationRuntimeSettingsController.class, "approve",
            "novel:gamification:settings:approve", long.class,
            GamificationRuntimeSettingsController.ActionRequest.class);
        assertPermission(GamificationRuntimeSettingsController.class, "schedule",
            "novel:gamification:settings:activate", long.class,
            GamificationRuntimeSettingsController.ScheduleRequest.class);

        assertPermission(GamificationPolicyController.class, "list",
            "novel:gamification:policy:view", int.class);
        assertPermission(GamificationPolicyController.class, "createDraft",
            "novel:gamification:policy:edit",
            GamificationPolicyController.CreateDraftRequest.class);
        assertPermission(GamificationPolicyController.class, "approve",
            "novel:gamification:policy:approve", String.class,
            GamificationPolicyController.ActionRequest.class);
        assertPermission(GamificationPolicyController.class, "publish",
            "novel:gamification:policy:publish", String.class,
            GamificationPolicyController.ActionRequest.class);
    }

    @Test
    void adminTemplatesUseNonceI18nAndPermissionSafeJavascript() throws Exception {
        String include = read("src/main/resources/templates/include.html");
        String settingsTemplate = read(
            "src/main/resources/templates/novel/gamification/settings.html");
        String policyTemplate = read(
            "src/main/resources/templates/novel/gamification/policies.html");
        String settingsScript = read(
            "src/main/resources/static/js/appjs/novel/gamification/settings.js");
        String policyScript = read(
            "src/main/resources/static/js/appjs/novel/gamification/policies.js");

        assertThat(include).contains(
            "<script th:inline=\"javascript\" th:attr=\"nonce=${cspNonce}\">");
        assertThat(settingsTemplate)
            .contains("th:attr=\"nonce=${cspNonce}\"")
            .contains("admin.gamification.settings.field.policyVersion")
            .contains("admin.gamification.settings.unit.milliseconds")
            .contains("id=\"approvalQueue\"", "id=\"selectedCreatedBy\"")
            .contains("admin.gamification.settings.diffBefore");
        assertThat(policyTemplate)
            .contains("th:attr=\"nonce=${cspNonce}\"")
            .contains("window.GamificationPolicyPermissions")
            .contains("pattern=\"[a-z0-9][a-z0-9._-]{0,31}\"")
            .contains("admin.gamification.policyStudio.versionFormat")
            .contains("admin.gamification.policyStudio.tableVersion")
            .contains("admin.gamification.policyStudio.metadataHash")
            .contains("admin.gamification.policyStudio.saveLevel")
            .contains("admin.gamification.policyStudio.saveQuest")
            .contains("admin.gamification.policyStudio.tabPublic");

        assertThat(settingsScript)
            .contains("function setDisabled(", "function bind(", "function formatDate(")
            .contains("text.fields[fieldName]", "text.units[unitName]")
            .contains("function applyConstraints(", "diff.before[key]", "diff.after[key]")
            .contains("diff.earliestEffectiveAtMillis", "row.status === 'PENDING_APPROVAL'")
            .contains("action('cancel')", "rollback")
            .doesNotContain("HIGH-RISK", "innerHTML");
        assertThat(policyScript)
            .contains("function setDisabled(", "function bind(")
            .contains("text.metadata.version", "window.GamificationPolicyPermissions.edit")
            .doesNotContain("'version='", "'createdBy='", "innerHTML");
    }

    @Test
    void runtimeApiSourceDoesNotExposeSecretValueOrPath() throws Exception {
        String controller = read(
            "src/main/java/com/java2nb/novel/controller/GamificationRuntimeSettingsController.java");

        assertThat(controller)
            .contains("hashSecretReady", "configuredKeyId", "requiredKeyId")
            .doesNotContain("getVoteIpHashSaltFile()", "voteIpHashSaltFile");
    }

    private void assertPermission(Class<?> type, String methodName, String permission,
                                  Class<?>... parameterTypes) throws Exception {
        Method method = type.getDeclaredMethod(methodName, parameterTypes);
        RequiresPermissions annotation = method.getAnnotation(RequiresPermissions.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).containsExactly(permission);
    }

    private String read(String relativePath) throws Exception {
        return Files.readString(module.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
