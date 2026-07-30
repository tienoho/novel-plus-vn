package com.java2nb.novel.controller;

import com.java2nb.common.annotation.Log;
import com.java2nb.novel.config.ReadingSubscriptionAdminProperties;
import com.java2nb.novel.service.subscription.ReadingSubscriptionService;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ReadingSubscriptionAdminControllerTest {

    @Test
    void separatesViewConfigAndActivationPermissionsWithAudit() throws Exception {
        assertPermission("index", "novel:readingSubscription:view");
        assertPermission("listPlans", "novel:readingSubscription:view");
        assertPermission("getCurrent", "novel:readingSubscription:view", long.class);
        assertPermission("listPurchaseReviews", "novel:readingSubscription:review",
            String.class, int.class, int.class);
        assertAudited("createPlan", "novel:readingSubscription:config",
            String.class, String.class, long.class, long.class, int.class, int.class);
        assertAudited("updatePlan", "novel:readingSubscription:config",
            long.class, long.class, String.class, String.class, long.class, long.class,
            int.class, int.class);
        assertAudited("changePlanStatus", "novel:readingSubscription:config",
            long.class, long.class, String.class);
        assertAudited("activate", "novel:readingSubscription:activate",
            long.class, String.class, long.class, Long.class, String.class);
        assertAudited("retryPurchaseActivation", "novel:readingSubscription:review",
            long.class, long.class, String.class);
        assertAudited("sendPurchaseToRefund", "novel:readingSubscription:review",
            long.class, long.class, String.class);
    }

    @Test
    void manualActivationIsFailClosedByDefault() {
        ReadingSubscriptionService service = mock(ReadingSubscriptionService.class);
        ReadingSubscriptionAdminProperties properties = new ReadingSubscriptionAdminProperties();
        ReadingSubscriptionAdminController controller =
            new ReadingSubscriptionAdminController(service, properties);

        assertThatThrownBy(() -> controller.activate(
            11L, "BASIC_MONTHLY", 1_798_761_600_000L, null, "request_0001"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("đang tắt");
        verify(service, never()).activate(any());
    }

    private void assertPermission(String methodName, String permission,
                                  Class<?>... parameterTypes) throws Exception {
        Method method = ReadingSubscriptionAdminController.class
            .getDeclaredMethod(methodName, parameterTypes);
        RequiresPermissions annotation = method.getAnnotation(RequiresPermissions.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).containsExactly(permission);
    }

    private void assertAudited(String methodName, String permission,
                               Class<?>... parameterTypes) throws Exception {
        Method method = ReadingSubscriptionAdminController.class
            .getDeclaredMethod(methodName, parameterTypes);
        assertPermission(methodName, permission, parameterTypes);
        assertThat(method.getAnnotation(Log.class)).isNotNull();
    }
}
