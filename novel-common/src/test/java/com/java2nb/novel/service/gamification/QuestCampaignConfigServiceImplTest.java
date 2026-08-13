package com.java2nb.novel.service.gamification;

import com.java2nb.novel.mapper.QuestCampaignConfigMapper;
import com.java2nb.novel.service.impl.QuestCampaignConfigServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestCampaignConfigServiceImplTest {

    private QuestCampaignConfigMapper mapper;
    private QuestCampaignConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(QuestCampaignConfigMapper.class);
        service = new QuestCampaignConfigServiceImpl(mapper);
    }

    @Test
    void createsImmutableDraftWindow() {
        QuestCampaignDraftCommand command = draft("SUMMER_2027");
        QuestCampaignRow created = campaign(71L, "SUMMER_2027", "DRAFT");
        when(mapper.insertDraft(command)).thenReturn(1);
        when(mapper.selectByCode("SUMMER_2027")).thenReturn(created);

        assertThat(service.createDraft(command)).isSameAs(created);
    }

    @Test
    void replacesRewardsOnlyWhileCampaignIsDraft() {
        QuestCampaignRow active = campaign(71L, "SUMMER_2027", "ACTIVE");
        when(mapper.lockById(71L)).thenReturn(active);

        assertThatThrownBy(() -> service.replaceReward(
            71L, new QuestRewardCommand("DAILY_READING", 20L, 2L)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("DRAFT");

        verify(mapper, never()).deleteRewardTypes("SUMMER_2027", "DAILY_READING", "v1");
    }

    @Test
    void activatesDraftOnlyWhenRewardExistsAndWindowDoesNotOverlap() {
        QuestCampaignRow draft = campaign(71L, "SUMMER_2027", "DRAFT");
        when(mapper.lockById(71L)).thenReturn(draft);
        when(mapper.countRewards("SUMMER_2027", "v1")).thenReturn(2);
        when(mapper.countOverlappingActive(71L, draft.getStartAt(), draft.getEndAt())).thenReturn(0);
        when(mapper.updateStatus(71L, "DRAFT", "ACTIVE")).thenReturn(1);
        QuestCampaignRow active = campaign(71L, "SUMMER_2027", "ACTIVE");
        when(mapper.selectById(71L)).thenReturn(active);

        assertThat(service.activate(71L, Date.from(Instant.parse("2027-05-01T00:00:00Z"))))
            .isSameAs(active);
    }

    @Test
    void rejectsOverlappingCampaignBeforeActivation() {
        QuestCampaignRow draft = campaign(71L, "SUMMER_2027", "DRAFT");
        when(mapper.lockById(71L)).thenReturn(draft);
        when(mapper.countRewards("SUMMER_2027", "v1")).thenReturn(1);
        when(mapper.countOverlappingActive(71L, draft.getStartAt(), draft.getEndAt())).thenReturn(1);

        assertThatThrownBy(() -> service.activate(
            71L, Date.from(Instant.parse("2027-05-01T00:00:00Z"))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("chồng lấn");

        verify(mapper, never()).updateStatus(71L, "DRAFT", "ACTIVE");
    }

    private QuestCampaignDraftCommand draft(String code) {
        return new QuestCampaignDraftCommand(code,
            Date.from(Instant.parse("2027-06-01T00:00:00Z")),
            Date.from(Instant.parse("2027-07-01T00:00:00Z")), "v1");
    }

    private QuestCampaignRow campaign(long id, String code, String status) {
        QuestCampaignRow row = new QuestCampaignRow();
        row.setId(id);
        row.setCampaignCode(code);
        row.setStartAt(Date.from(Instant.parse("2027-06-01T00:00:00Z")));
        row.setEndAt(Date.from(Instant.parse("2027-07-01T00:00:00Z")));
        row.setStatus(status);
        row.setPolicyVersion("v1");
        return row;
    }
}
