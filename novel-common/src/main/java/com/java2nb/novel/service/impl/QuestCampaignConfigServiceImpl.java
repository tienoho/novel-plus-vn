package com.java2nb.novel.service.impl;

import com.java2nb.novel.mapper.QuestCampaignConfigMapper;
import com.java2nb.novel.service.gamification.QuestCampaignConfigService;
import com.java2nb.novel.service.gamification.QuestCampaignDraftCommand;
import com.java2nb.novel.service.gamification.QuestCampaignRow;
import com.java2nb.novel.service.gamification.QuestRewardCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
@RequiredArgsConstructor
public class QuestCampaignConfigServiceImpl implements QuestCampaignConfigService {
    private final QuestCampaignConfigMapper mapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuestCampaignRow createDraft(QuestCampaignDraftCommand command) {
        if (command == null || mapper.insertDraft(command) != 1) {
            throw new IllegalStateException("Không thể tạo campaign nhiệm vụ");
        }
        QuestCampaignRow created = mapper.selectByCode(command.campaignCode());
        if (created == null) {
            throw new IllegalStateException("Không đọc được campaign nhiệm vụ vừa tạo");
        }
        return created;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceReward(long campaignId, QuestRewardCommand command) {
        if (campaignId <= 0 || command == null) {
            throw new IllegalArgumentException("Campaign hoặc reward nhiệm vụ không hợp lệ");
        }
        QuestCampaignRow campaign = requireLocked(campaignId);
        if (!"DRAFT".equals(campaign.getStatus())) {
            throw new IllegalStateException("Chỉ campaign DRAFT mới được sửa reward");
        }
        if (mapper.countQuest(command.questCode()) != 1) {
            throw new IllegalArgumentException("Nhiệm vụ không tồn tại hoặc đã tắt");
        }
        mapper.deleteRewardTypes(campaign.getCampaignCode(), command.questCode());
        if (command.expAmount() > 0
            && mapper.insertReward(campaign.getCampaignCode(), command.questCode(),
            "EXP", command.expAmount()) != 1) {
            throw new IllegalStateException("Không thể lưu reward EXP");
        }
        if (command.ticketAmount() > 0
            && mapper.insertReward(campaign.getCampaignCode(), command.questCode(),
            "TICKET", command.ticketAmount()) != 1) {
            throw new IllegalStateException("Không thể lưu reward Đuốc");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.SERIALIZABLE)
    public QuestCampaignRow activate(long campaignId, Date activatedAt) {
        if (campaignId <= 0 || activatedAt == null) {
            throw new IllegalArgumentException("Yêu cầu kích hoạt campaign không hợp lệ");
        }
        QuestCampaignRow campaign = requireLocked(campaignId);
        if ("ACTIVE".equals(campaign.getStatus())) {
            return campaign;
        }
        if (!"DRAFT".equals(campaign.getStatus())) {
            throw new IllegalStateException("Chỉ campaign DRAFT mới được kích hoạt");
        }
        if (!campaign.getEndAt().after(activatedAt)) {
            throw new IllegalStateException("Campaign đã hết cửa sổ kích hoạt");
        }
        if (mapper.countRewards(campaign.getCampaignCode()) <= 0) {
            throw new IllegalStateException("Campaign chưa có reward");
        }
        if (mapper.countOverlappingActive(campaignId, campaign.getStartAt(), campaign.getEndAt()) > 0) {
            throw new IllegalStateException("Campaign nhiệm vụ ACTIVE bị chồng lấn");
        }
        if (mapper.updateStatus(campaignId, "DRAFT", "ACTIVE") != 1) {
            throw new IllegalStateException("Campaign đã được cập nhật đồng thời");
        }
        return requireSelected(campaignId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuestCampaignRow close(long campaignId) {
        QuestCampaignRow campaign = requireLocked(campaignId);
        if ("CLOSED".equals(campaign.getStatus())) {
            return campaign;
        }
        if (!"ACTIVE".equals(campaign.getStatus())
            || mapper.updateStatus(campaignId, "ACTIVE", "CLOSED") != 1) {
            throw new IllegalStateException("Chỉ campaign ACTIVE mới được đóng");
        }
        return requireSelected(campaignId);
    }

    private QuestCampaignRow requireLocked(long campaignId) {
        QuestCampaignRow campaign = mapper.lockById(campaignId);
        if (campaign == null) {
            throw new IllegalArgumentException("Không tìm thấy campaign nhiệm vụ");
        }
        return campaign;
    }

    private QuestCampaignRow requireSelected(long campaignId) {
        QuestCampaignRow campaign = mapper.selectById(campaignId);
        if (campaign == null) {
            throw new IllegalStateException("Không đọc được campaign nhiệm vụ sau cập nhật");
        }
        return campaign;
    }
}
