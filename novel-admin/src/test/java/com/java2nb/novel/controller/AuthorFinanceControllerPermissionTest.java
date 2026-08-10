package com.java2nb.novel.controller;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorFinanceControllerPermissionTest {

    @Test
    void separatesViewPiiKycAndPayoutPermissions() throws Exception {
        assertPermission("index", "novel:authorFinance:view");
        assertPermission("listKyc", "novel:authorFinance:view", Map.class);
        assertPermission("getKyc", "novel:authorFinance:pii", long.class);
        assertPermission("approveKyc", "novel:authorFinance:kyc", long.class, int.class);
        assertPermission("listWithdrawals", "novel:authorFinance:view", Map.class);
        assertPermission("getWithdrawal", "novel:authorFinance:view", long.class);
        assertPermission("getWithdrawalPayoutDetail", "novel:authorFinance:pii", long.class);
        assertPermission("approveWithdrawal", "novel:authorFinance:payout:approve",
            long.class, long.class, long.class);
        assertPermission("rejectWithdrawal", "novel:authorFinance:payout:approve",
            long.class, long.class, String.class);
        assertPermission("markProcessing", "novel:authorFinance:payout:execute",
            long.class, long.class);
        assertPermission("markPaid", "novel:authorFinance:payout:execute",
            long.class, long.class, String.class);
        assertPermission("markFailed", "novel:authorFinance:payout:execute",
            long.class, long.class, String.class);
        assertPermission("autoPayout", "novel:authorFinance:payout:execute", long.class);
    }

    private void assertPermission(String methodName, String permission, Class<?>... parameterTypes) throws Exception {
        Method method = AuthorFinanceController.class.getDeclaredMethod(methodName, parameterTypes);
        RequiresPermissions annotation = method.getAnnotation(RequiresPermissions.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).containsExactly(permission);
    }
}
