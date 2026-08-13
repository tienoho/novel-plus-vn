package com.java2nb.novel.controller;

import com.java2nb.novel.common.annotation.AuditLog;
import com.java2nb.novel.common.entity.User2faDO;
import com.java2nb.novel.common.service.TotpService;
import com.java2nb.novel.core.enums.ResponseStatus;
import com.java2nb.novel.core.bean.UserDetails;
import com.java2nb.novel.core.utils.AuthCookieService;
import com.java2nb.novel.core.utils.RefreshTokenSessionService;
import com.java2nb.novel.entity.User;
import com.java2nb.novel.service.UserService;
import io.github.xxyopen.model.resp.RestResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class TwoFactorUserController {

    private final TotpService totpService;
    private final UserService userService;
    private final RefreshTokenSessionService refreshTokenSessionService;
    private final AuthCookieService authCookieService;

    @PostMapping("/api/2fa/setup")
    @AuditLog(module = "AUTH", eventType = "AUTH_2FA_SETUP", detail = "Khoi tao bi mat 2FA TOTP")
    public RestResult setup2fa(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String username = (String) request.getAttribute("username");
        if (userId == null) {
            return RestResult.fail(ResponseStatus.NO_LOGIN);
        }

        User2faDO user2fa = totpService.setup2fa(userId, username);
        String qrCodeUri = totpService.getQrCodeUri(username, user2fa.getSecretKeyCiphertext());

        Map<String, Object> data = new HashMap<>();
        data.put("secretKey", user2fa.getSecretKeyCiphertext());
        data.put("qrCodeUri", qrCodeUri);
        data.put("isEnabled", user2fa.getIsEnabled());

        return RestResult.ok(data);
    }

    @PostMapping("/api/2fa/enable")
    @AuditLog(module = "AUTH", eventType = "AUTH_2FA_ENABLE", detail = "Kich hoat 2FA TOTP thanh cong")
    public RestResult enable2fa(HttpServletRequest request, @RequestParam String code) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return RestResult.fail(ResponseStatus.NO_LOGIN);
        }

        List<String> backupCodes = totpService.enable2fa(userId, code);

        Map<String, Object> data = new HashMap<>();
        data.put("isEnabled", true);
        data.put("backupCodes", backupCodes);

        return RestResult.ok(data);
    }

    @PostMapping("/api/2fa/disable")
    @AuditLog(module = "AUTH", eventType = "AUTH_2FA_DISABLE", detail = "Tắt 2FA TOTP")
    public RestResult disable2fa(HttpServletRequest request, @RequestParam String code) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return RestResult.fail(ResponseStatus.NO_LOGIN);
        }

        totpService.disable2fa(userId, code);
        return RestResult.ok();
    }

    @PostMapping("/login/2fa")
    @AuditLog(module = "AUTH", eventType = "AUTH_2FA_CHALLENGE", detail = "Xac thuc buoc 2 2FA TOTP")
    public RestResult login2fa(@RequestParam String preAuthToken, @RequestParam String code,
                               HttpServletResponse response) {
        Long userId = totpService.verifyPreAuthToken(preAuthToken);
        if (userId == null) {
            return RestResult.fail(ResponseStatus.VEL_CODE_ERROR);
        }

        if (!totpService.verify2faOrBackupCode(userId, code)) {
            return RestResult.fail(ResponseStatus.VEL_CODE_ERROR);
        }

        User user = userService.userInfo(userId);
        if (user == null) {
            return RestResult.fail(ResponseStatus.NO_LOGIN);
        }
        UserDetails details = new UserDetails();
        details.setId(userId);
        details.setUsername(user.getUsername());
        details.setNickName(user.getNickName());
        authCookieService.write(response, refreshTokenSessionService.issue(details));
        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        data.put("authVerified", true);
        return RestResult.ok(data);
    }
}
