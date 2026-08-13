package com.java2nb.novel.service.gift;

public interface GiftCodeService {
    GiftRedemptionResult redeem(GiftRedeemCommand command);
    java.util.List<GiftCampaignRow> listCampaigns();
    GiftCampaignRow createCampaign(GiftCampaignCommand command);
    GiftCampaignRow changeCampaignStatus(long campaignId, long expectedVersion, String status);
    java.util.List<String> issueCodes(long campaignId, int quantity, long maxRedemptionsPerCode);
    GiftCodePage listCodes(long campaignId, int page, int pageSize);
    GiftCodeRow revokeCode(long codeId, long expectedVersion);
    GiftRedemptionHistoryPage listUserRedemptions(long userId, int page, int pageSize);
    GiftRedemptionHistoryPage listCampaignRedemptions(long campaignId, int page, int pageSize);
}
