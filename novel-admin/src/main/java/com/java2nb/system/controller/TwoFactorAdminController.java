package com.java2nb.system.controller;

import com.java2nb.common.controller.BaseController;
import com.java2nb.common.utils.R;
import com.java2nb.novel.common.annotation.AuditLog;
import com.java2nb.novel.common.entity.User2faDO;
import com.java2nb.novel.common.service.TotpService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/system/2fa")
@RequiredArgsConstructor
public class TwoFactorAdminController extends BaseController {

    private final TotpService totpService;

    @PostMapping("/setup")
    @AuditLog(module = "AUTH", eventType = "ADMIN_2FA_SETUP", detail = "Admin khoi tao 2FA TOTP")
    public R setup2fa() {
        Long userId = getUserId();
        String username = getUsername();

        User2faDO user2fa = totpService.setup2fa(userId, username);
        String qrCodeUri = totpService.getQrCodeUri(username, user2fa.getSecretKeyCiphertext());

        Map<String, Object> data = new HashMap<>();
        data.put("secretKey", user2fa.getSecretKeyCiphertext());
        data.put("qrCodeUri", qrCodeUri);
        data.put("isEnabled", user2fa.getIsEnabled());

        return R.ok().put("data", data);
    }

    @PostMapping("/enable")
    @AuditLog(module = "AUTH", eventType = "ADMIN_2FA_ENABLE", detail = "Admin kich hoat 2FA TOTP")
    public R enable2fa(@RequestParam String code) {
        Long userId = getUserId();
        List<String> backupCodes = totpService.enable2fa(userId, code);

        Map<String, Object> data = new HashMap<>();
        data.put("isEnabled", true);
        data.put("backupCodes", backupCodes);

        return R.ok().put("data", data);
    }

    @PostMapping("/disable")
    @AuditLog(module = "AUTH", eventType = "ADMIN_2FA_DISABLE", detail = "Admin tat 2FA TOTP")
    public R disable2fa(@RequestParam String code) {
        Long userId = getUserId();
        totpService.disable2fa(userId, code);
        return R.ok();
    }
}
