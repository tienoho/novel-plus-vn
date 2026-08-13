package com.java2nb.novel.controller;

import com.java2nb.novel.core.exception.BusinessException;
import com.java2nb.novel.dto.gamification.TickerEntryResponse;
import com.java2nb.novel.service.gamification.MonthlyTicketService;
import com.java2nb.novel.service.gamification.TickerEntryRow;
import com.java2nb.novel.service.gamification.config.GamificationConfigProvider;
import com.java2nb.novel.service.gamification.config.GamificationConfigSnapshot;
import io.github.xxyopen.model.resp.RestResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GamificationTickerControllerTest {

    private MonthlyTicketService monthlyTicketService;
    private GamificationConfigProvider configProvider;
    private GamificationTickerController controller;

    @BeforeEach
    void setUp() {
        monthlyTicketService = mock(MonthlyTicketService.class);
        configProvider = mock(GamificationConfigProvider.class);
        when(configProvider.current()).thenReturn(
            GamificationConfigSnapshot.bootstrapDisabled().toBuilder().voteEnabled(true).build());
        controller = new GamificationTickerController(monthlyTicketService, configProvider);
    }

    @Test
    void returnsNicknameBookNameAndTicketCountOnlyNoIdentifiers() {
        TickerEntryRow row = new TickerEntryRow();
        row.setNickname("DocGiaChanChinh");
        row.setBookName("Truyện A");
        row.setTicketCount(3L);
        when(monthlyTicketService.listTicker(20)).thenReturn(List.of(row));

        RestResult<List<TickerEntryResponse>> response = controller.getTicker(20);

        assertThat(response.getData()).singleElement().satisfies(entry -> {
            assertThat(entry.nickname()).isEqualTo("DocGiaChanChinh");
            assertThat(entry.bookName()).isEqualTo("Truyện A");
            assertThat(entry.ticketCount()).isEqualTo(3L);
        });
        verify(monthlyTicketService).listTicker(20);
    }

    @Test
    void refusesWhenVotingIsDisabled() {
        when(configProvider.current()).thenReturn(GamificationConfigSnapshot.bootstrapDisabled());

        assertThatThrownBy(() -> controller.getTicker(20)).isInstanceOf(BusinessException.class);
    }
}
