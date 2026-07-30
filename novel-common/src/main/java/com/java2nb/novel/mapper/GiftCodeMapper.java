package com.java2nb.novel.mapper;

import com.java2nb.novel.service.gift.GiftCodeClaimRow;
import com.java2nb.novel.service.gift.GiftCodeRow;
import com.java2nb.novel.service.gift.GiftCodeHashCandidate;
import com.java2nb.novel.service.gift.GiftCampaignCommand;
import com.java2nb.novel.service.gift.GiftCampaignRow;
import com.java2nb.novel.service.gift.GiftRedemptionInsert;
import com.java2nb.novel.service.gift.GiftRedemptionHistoryRow;
import com.java2nb.novel.service.gift.GiftRedemptionRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface GiftCodeMapper {
    java.util.List<GiftCampaignRow> selectCampaigns();
    GiftCampaignRow selectCampaignById(@Param("campaignId") long campaignId);
    GiftCampaignRow selectCampaignByCode(@Param("campaignCode") String campaignCode);
    int insertCampaign(@Param("command") GiftCampaignCommand command,
                       @Param("policyVersion") String policyVersion);
    int updateCampaignStatus(@Param("campaignId") long campaignId,
                             @Param("expectedVersion") long expectedVersion,
                             @Param("status") String status);
    int insertCode(@Param("campaignId") long campaignId,
                   @Param("hmacKeyId") String hmacKeyId,
                   @Param("codeHash") String codeHash,
                   @Param("codeHint") String codeHint,
                   @Param("maxRedemptions") long maxRedemptions);
    GiftCodeClaimRow lockClaimByHashes(
        @Param("candidates") java.util.List<GiftCodeHashCandidate> candidates);
    int countCodesByHashes(
        @Param("candidates") java.util.List<GiftCodeHashCandidate> candidates);
    GiftRedemptionRow selectRedemptionByCodeUser(@Param("codeId") long codeId,
                                                 @Param("userId") long userId);
    int countCampaignUserRedemptions(@Param("campaignId") long campaignId,
                                     @Param("userId") long userId);
    int insertWalletRedemption(@Param("row") GiftRedemptionInsert row,
                               @Param("idempotencyKey") String idempotencyKey);
    int insertTicketRedemption(@Param("row") GiftRedemptionInsert row,
                               @Param("idempotencyKey") String idempotencyKey);
    int incrementCodeRedemption(@Param("codeId") long codeId,
                                @Param("maxRedemptions") long maxRedemptions);
    int incrementCampaignRedemption(@Param("campaignId") long campaignId,
                                    @Param("maxRedemptions") long maxRedemptions);
    GiftRedemptionRow selectRedemptionByCodeUserCurrent(@Param("codeId") long codeId,
                                                        @Param("userId") long userId);
    GiftRedemptionRow selectRedemptionByUserRequestCurrent(@Param("userId") long userId,
                                                           @Param("clientRequestId") String clientRequestId);
    long countCodesByCampaign(@Param("campaignId") long campaignId);
    java.util.List<GiftCodeRow> selectCodesByCampaign(@Param("campaignId") long campaignId,
                                                      @Param("offset") long offset,
                                                      @Param("limit") int limit);
    GiftCodeRow selectCodeById(@Param("codeId") long codeId);
    int revokeUnusedCode(@Param("codeId") long codeId,
                         @Param("expectedVersion") long expectedVersion);
    long countRedemptionsByUser(@Param("userId") long userId);
    java.util.List<GiftRedemptionHistoryRow> selectRedemptionsByUser(
        @Param("userId") long userId, @Param("offset") long offset, @Param("limit") int limit);
    long countRedemptionsByCampaign(@Param("campaignId") long campaignId);
    java.util.List<GiftRedemptionHistoryRow> selectRedemptionsByCampaign(
        @Param("campaignId") long campaignId, @Param("offset") long offset,
        @Param("limit") int limit);
}
