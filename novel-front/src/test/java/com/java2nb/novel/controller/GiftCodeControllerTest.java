package com.java2nb.novel.controller;

import com.java2nb.novel.core.bean.UserDetails;
import com.java2nb.novel.core.exception.BusinessException;
import com.java2nb.novel.dto.gift.GiftCodeRedeemRequest;
import com.java2nb.novel.service.gift.GiftCodeProperties;
import com.java2nb.novel.service.gift.GiftCodeService;
import com.java2nb.novel.service.gift.GiftRedemptionResult;
import com.java2nb.novel.service.gift.GiftRedemptionHistoryPage;
import com.java2nb.novel.service.gift.GiftRedemptionHistoryRow;
import com.java2nb.novel.service.gift.GiftRedemptionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GiftCodeControllerTest {
    private static final Clock CLOCK = Clock.fixed(
        Instant.parse("2027-01-01T00:00:00Z"), ZoneOffset.UTC);
    private GiftCodeService service;
    private GiftCodeProperties properties;
    private GiftCodeController controller;

    @BeforeEach
    void setUp() {
        service = mock(GiftCodeService.class);
        properties = new GiftCodeProperties();
        properties.setEnabled(true);
        properties.setHmacSecret("gift-test-secret-at-least-32-characters");
        UserDetails user = mock(UserDetails.class);
        when(user.getId()).thenReturn(11L);
        controller = new GiftCodeController(service, properties, CLOCK) {
            @Override
            protected UserDetails getUserDetails(jakarta.servlet.http.HttpServletRequest request) {
                return user;
            }
        };
    }

    @Test
    void redeemUsesSessionUserAndServerTime() {
        when(service.redeem(any())).thenReturn(new GiftRedemptionResult(
            GiftRedemptionStatus.POSTED, 31L, "XU", 10L));

        var response = controller.redeem(new GiftCodeRedeemRequest(
            "ABCD-EFGH-JKLM-NPQR", "request_0001"), new MockHttpServletRequest());

        assertThat(response.getData().rewardType()).isEqualTo("XU");
        ArgumentCaptor<com.java2nb.novel.service.gift.GiftRedeemCommand> command =
            ArgumentCaptor.forClass(com.java2nb.novel.service.gift.GiftRedeemCommand.class);
        verify(service).redeem(command.capture());
        assertThat(command.getValue().userId()).isEqualTo(11L);
        assertThat(command.getValue().occurredAt().toInstant()).isEqualTo(CLOCK.instant());
    }

    @Test
    void disabledFeatureRejectsBeforeHashingOrPostingReward() {
        properties.setEnabled(false);

        assertThatThrownBy(() -> controller.redeem(new GiftCodeRedeemRequest(
            "ABCD-EFGH-JKLM-NPQR", "request_0002"), new MockHttpServletRequest()))
            .isInstanceOf(BusinessException.class);
        verify(service, never()).redeem(any());
    }

    @Test
    void historyUsesSessionOwnerAndRemainsReadableWhenRedemptionIsDisabled() {
        properties.setEnabled(false);
        GiftRedemptionHistoryRow row = new GiftRedemptionHistoryRow();
        row.setId(41L);
        row.setUserId(11L);
        row.setCampaignName("Quà Tết");
        row.setCodeHint("2345");
        row.setRewardType("XU");
        row.setRewardAmount(25L);
        row.setRedeemedAt(Date.from(CLOCK.instant()));
        when(service.listUserRedemptions(11L, 1, 20))
            .thenReturn(new GiftRedemptionHistoryPage(List.of(row), 1, 1, 20));

        var response = controller.listRedemptions(1, 20, new MockHttpServletRequest());

        assertThat(response.getData().items()).hasSize(1);
        assertThat(response.getData().items().get(0).campaignName()).isEqualTo("Quà Tết");
        assertThat(response.getData().items().get(0).receiptId()).isEqualTo(41L);
        verify(service).listUserRedemptions(11L, 1, 20);
    }
}
