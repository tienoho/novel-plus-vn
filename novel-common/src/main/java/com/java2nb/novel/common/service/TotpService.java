package com.java2nb.novel.common.service;

import com.java2nb.novel.common.entity.User2faDO;

import java.util.List;

public interface TotpService {

    String generateSecretKey();

    String getQrCodeUri(String username, String secretKey);

    boolean verifyTotp(String secretKey, String code);

    List<String> generateBackupCodes();

    boolean is2faEnabled(Long userId);

    User2faDO setup2fa(Long userId, String username);

    List<String> enable2fa(Long userId, String code);

    boolean disable2fa(Long userId, String code);

    boolean verify2faOrBackupCode(Long userId, String code);

    String createPreAuthToken(Long userId, String username);

    Long verifyPreAuthToken(String preAuthToken);
}
