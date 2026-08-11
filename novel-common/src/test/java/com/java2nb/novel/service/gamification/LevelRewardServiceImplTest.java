package com.java2nb.novel.service.gamification;

import com.java2nb.novel.mapper.GamificationProgressMapper;
import com.java2nb.novel.mapper.MonthlyTicketMapper;
import com.java2nb.novel.service.impl.LevelRewardServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LevelRewardServiceImplTest {

    private static final long EVENT_ID = 91L;
    private static final long USER_ID = 11L;

    private GamificationProgressMapper mapper;
    private MonthlyTicketService monthlyTicketService;
    private MonthlyTicketMapper monthlyTicketMapper;
    private LevelRewardServiceImpl service;
    private GamificationEventRow event;
    private LevelRewardPolicyRow policy;
    private LevelRewardGrantRow grant;

    @BeforeEach
    void setUp() {
        mapper = mock(GamificationProgressMapper.class);
        monthlyTicketService = mock(MonthlyTicketService.class);
        monthlyTicketMapper = mock(MonthlyTicketMapper.class);
        service = new LevelRewardServiceImpl(mapper, monthlyTicketService, monthlyTicketMapper);

        event = new GamificationEventRow();
        event.setId(EVENT_ID);
        event.setEventType("LEVEL_REACHED");
        event.setSourceKey("GAMIFY:LEVEL_REACHED:11:3:v1");
        event.setUserId(USER_ID);
        event.setOccurredAt(Date.from(Instant.parse("2026-08-01T00:00:00Z")));
        event.setLocalDate(LocalDate.of(2026, 8, 1));
        event.setPayloadJson("{\"level\":3}");
        event.setPolicyVersion("reward-v2");
        event.setRuntimeConfigRevision(7L);

        policy = new LevelRewardPolicyRow();
        policy.setPolicyVersion("reward-v2");
        policy.setLevel(3);
        policy.setTicketAmount(2L);
        policy.setTicketValidityDays(60);

        grant = new LevelRewardGrantRow();
        grant.setEventId(EVENT_ID);
        grant.setUserId(USER_ID);
        grant.setLevel(3);
        grant.setPolicyVersion("reward-v2");
        grant.setTicketAmount(2L);
        grant.setTicketLedgerId(501L);
        grant.setIdempotencyKey("LEVEL_UP:11:3:reward-v2");
    }

    @Test
    void grantsThroughTicketLedgerAndRecordsIdempotentResult() {
        TicketLedgerRow ledger = new TicketLedgerRow();
        ledger.setId(501L);
        when(mapper.selectLevelRewardPolicy("reward-v2", 3)).thenReturn(policy);
        when(mapper.selectLevelRewardGrant(USER_ID, 3, "reward-v2"))
            .thenReturn(null, grant);
        when(monthlyTicketService.grant(org.mockito.ArgumentMatchers.any()))
            .thenReturn(TicketPostResult.POSTED);
        when(monthlyTicketMapper.selectLedgerByIdempotencyKey("LEVEL_UP:11:3:reward-v2"))
            .thenReturn(ledger);
        when(mapper.insertLevelRewardGrantIgnore(EVENT_ID, USER_ID, 3, "reward-v2", 2L,
            501L, "LEVEL_UP:11:3:reward-v2")).thenReturn(1);

        assertThat(service.apply(event)).isEqualTo(1);

        ArgumentCaptor<TicketGrantCommand> command = ArgumentCaptor.forClass(TicketGrantCommand.class);
        verify(monthlyTicketService).grant(command.capture());
        assertThat(command.getValue().sourceType()).isEqualTo("LEVEL_UP");
        assertThat(command.getValue().amount()).isEqualTo(2L);
        assertThat(command.getValue().idempotencyKey()).isEqualTo("LEVEL_UP:11:3:reward-v2");
        assertThat(command.getValue().runtimeConfigRevision()).isEqualTo(7L);
        assertThat(command.getValue().expireAt()).isEqualTo(
            Date.from(Instant.parse("2026-09-30T00:00:00Z")));
    }

    @Test
    void replayDoesNotGrantTicketsAgain() {
        when(mapper.selectLevelRewardPolicy("reward-v2", 3)).thenReturn(policy);
        when(mapper.selectLevelRewardGrant(USER_ID, 3, "reward-v2")).thenReturn(grant);

        assertThat(service.apply(event)).isEqualTo(1);
        verify(monthlyTicketService, never()).grant(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void levelEventWithoutConfiguredPolicyIsSkipped() {
        assertThat(service.apply(event)).isZero();
        verify(monthlyTicketService, never()).grant(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsPayloadThatDoesNotMatchImmutableSourceIdentity() {
        event.setPayloadJson("{\"level\":4}");

        assertThatThrownBy(() -> service.apply(event))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("không khớp");
    }

    @Test
    void rejectsEventWithoutRuntimeRevisionBeforePostingReward() {
        event.setRuntimeConfigRevision(null);

        assertThatThrownBy(() -> service.apply(event))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("thiếu dữ liệu bắt buộc");
        verify(monthlyTicketService, never()).grant(org.mockito.ArgumentMatchers.any());
    }
}
