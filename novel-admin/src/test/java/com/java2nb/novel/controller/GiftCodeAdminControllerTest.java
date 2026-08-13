package com.java2nb.novel.controller;

import com.java2nb.common.annotation.Log;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class GiftCodeAdminControllerTest {
    @Test
    void separatesCampaignAndPlaintextIssuancePermissions() throws Exception {
        assertPermission("index", "novel:giftCode:view");
        assertPermission("listCampaigns", "novel:giftCode:view");
        assertPermission("listCodes", "novel:giftCode:view",
            long.class, int.class, int.class);
        assertPermission("listRedemptions", "novel:giftCode:view",
            long.class, int.class, int.class);
        assertAudited("createCampaign", "novel:giftCode:config", String.class, String.class,
            String.class, long.class, Integer.class, long.class, long.class, long.class, int.class);
        assertAudited("changeStatus", "novel:giftCode:config",
            long.class, long.class, String.class);
        assertAudited("issueCodes", "novel:giftCode:issue", long.class, int.class, long.class);
        assertAudited("revokeCode", "novel:giftCode:revoke", long.class, long.class);
    }

    private void assertPermission(String methodName, String permission,
                                  Class<?>... parameterTypes) throws Exception {
        Method method = GiftCodeAdminController.class.getDeclaredMethod(methodName, parameterTypes);
        RequiresPermissions annotation = method.getAnnotation(RequiresPermissions.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).containsExactly(permission);
    }

    private void assertAudited(String methodName, String permission,
                               Class<?>... parameterTypes) throws Exception {
        Method method = GiftCodeAdminController.class.getDeclaredMethod(methodName, parameterTypes);
        assertPermission(methodName, permission, parameterTypes);
        assertThat(method.getAnnotation(Log.class)).isNotNull();
    }
}
