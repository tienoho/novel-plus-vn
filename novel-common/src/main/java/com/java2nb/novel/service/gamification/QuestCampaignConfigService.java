package com.java2nb.novel.service.gamification;

import java.util.Date;

public interface QuestCampaignConfigService {
    QuestCampaignRow createDraft(QuestCampaignDraftCommand command);

    void replaceReward(long campaignId, QuestRewardCommand command);

    QuestCampaignRow activate(long campaignId, Date activatedAt);

    QuestCampaignRow close(long campaignId);
}
