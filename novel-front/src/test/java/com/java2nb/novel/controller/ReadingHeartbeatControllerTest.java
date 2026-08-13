package com.java2nb.novel.controller;

import com.java2nb.novel.core.bean.UserDetails;
import com.java2nb.novel.core.config.GamificationProperties;
import com.java2nb.novel.core.exception.BusinessException;
import com.java2nb.novel.dto.gamification.ReadingHeartbeatRequest;
import com.java2nb.novel.service.gamification.GamificationReadingHeartbeatService;
import com.java2nb.novel.service.gamification.ReadingHeartbeatInput;
import com.java2nb.novel.service.gamification.ReadingHeartbeatResult;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReadingHeartbeatControllerTest {

    private GamificationReadingHeartbeatService service;
    private GamificationProperties properties;
    private UserDetails user;
    private ReadingHeartbeatController controller;

    @BeforeEach
    void setUp() {
        service = mock(GamificationReadingHeartbeatService.class);
        properties = new GamificationProperties();
        properties.getEvent().setEnabled(true);
        properties.getQuest().setEnabled(true);
        user = mock(UserDetails.class);
        when(user.getId()).thenReturn(11L);
        controller = new ReadingHeartbeatController(service, properties) {
            @Override
            protected UserDetails getUserDetails(HttpServletRequest request) {
                return user;
            }
        };
    }

    @Test
    void usesAuthenticatedUserAndNeverAcceptsOwnerFromPayload() {
        ReadingHeartbeatRequest request = new ReadingHeartbeatRequest(
            "0123456789abcdef0123456789abcdef", 21L, 31L, 1, 60);
        ReadingHeartbeatInput input = request.toInput();
        ReadingHeartbeatResult result = new ReadingHeartbeatResult(request.sessionId(),
            60, 1, false, 2, false, List.of("source"));
        when(service.record(11L, input)).thenReturn(result);

        var response = controller.record(request, new MockHttpServletRequest());

        assertThat(response.getData().acceptedSeconds()).isEqualTo(60);
        assertThat(response.getData().verifiedMinutesToday()).isEqualTo(1);
        assertThat(response.getData().nextSequence()).isEqualTo(2);
        verify(service).record(11L, input);
    }

    @Test
    void rejectsAnonymousRequestBeforeCallingService() {
        user = null;

        assertThatThrownBy(() -> controller.record(validRequest(), new MockHttpServletRequest()))
            .isInstanceOf(BusinessException.class);

        verifyNoInteractions(service);
    }

    @Test
    void rejectsRequestWhenGamificationIsDisabledBeforeCallingService() {
        properties.getQuest().setEnabled(false);

        assertThatThrownBy(() -> controller.record(validRequest(), new MockHttpServletRequest()))
            .isInstanceOf(BusinessException.class);

        verifyNoInteractions(service);
    }

    private ReadingHeartbeatRequest validRequest() {
        return new ReadingHeartbeatRequest("0123456789abcdef0123456789abcdef",
            21L, 31L, 0, 0);
    }
}
