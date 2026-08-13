package com.java2nb.novel.common.service;

import com.java2nb.novel.common.dao.User2faDao;
import com.java2nb.novel.common.entity.User2faDO;
import com.java2nb.novel.common.service.impl.TotpServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class TotpServiceTest {

    private User2faDao user2faDao;
    private TotpServiceImpl totpService;

    @BeforeEach
    public void setUp() {
        user2faDao = mock(User2faDao.class);
        totpService = new TotpServiceImpl(user2faDao);
    }

    @Test
    public void testGenerateSecretKey() {
        String secret = totpService.generateSecretKey();
        assertNotNull(secret);
        assertTrue(secret.length() >= 26);
        assertTrue(secret.matches("^[A-Z2-7]+$"));
    }

    @Test
    public void testGetQrCodeUri() {
        String secret = "JBSWY3DPEHPK3PXP";
        String uri = totpService.getQrCodeUri("user_test", secret);

        assertNotNull(uri);
        assertTrue(uri.startsWith("otpauth://totp/KhoiThu:user_test"));
        assertTrue(uri.contains("secret=JBSWY3DPEHPK3PXP"));
        assertTrue(uri.contains("issuer=KhoiThu"));
    }

    @Test
    public void testGenerateBackupCodes() {
        List<String> codes = totpService.generateBackupCodes();
        assertNotNull(codes);
        assertEquals(8, codes.size());
        for (String c : codes) {
            assertEquals(8, c.length());
        }
    }

    @Test
    public void testPreAuthTokenLifecycle() {
        Long userId = 10023L;
        String username = "author_john";

        String token = totpService.createPreAuthToken(userId, username);
        assertNotNull(token);

        Long verifiedUserId = totpService.verifyPreAuthToken(token);
        assertEquals(userId, verifiedUserId);

        Long invalidVerification = totpService.verifyPreAuthToken("INVALID_TOKEN_STRING");
        assertNull(invalidVerification);
    }

    @Test
    public void testSetupAndEnable2fa() {
        Long userId = 456L;
        when(user2faDao.selectByUserId(userId)).thenReturn(null);

        User2faDO setupResult = totpService.setup2fa(userId, "test_user");
        assertNotNull(setupResult);
        assertEquals(userId, setupResult.getUserId());
        assertFalse(setupResult.getIsEnabled());
        verify(user2faDao, times(1)).insert(any(User2faDO.class));
    }

    @Test
    public void testIs2faEnabled() {
        User2faDO enabledDo = User2faDO.builder().userId(1L).isEnabled(true).build();
        User2faDO disabledDo = User2faDO.builder().userId(2L).isEnabled(false).build();

        when(user2faDao.selectByUserId(1L)).thenReturn(enabledDo);
        when(user2faDao.selectByUserId(2L)).thenReturn(disabledDo);
        when(user2faDao.selectByUserId(3L)).thenReturn(null);

        assertTrue(totpService.is2faEnabled(1L));
        assertFalse(totpService.is2faEnabled(2L));
        assertFalse(totpService.is2faEnabled(3L));
    }
}
