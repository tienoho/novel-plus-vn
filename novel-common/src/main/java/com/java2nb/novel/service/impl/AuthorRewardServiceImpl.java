package com.java2nb.novel.service.impl;

import com.java2nb.novel.mapper.AuthorRewardMapper;
import com.java2nb.novel.service.gamification.AuthorRewardAllocationRow;
import com.java2nb.novel.service.gamification.AuthorRewardService;
import com.java2nb.novel.service.gamification.MonthlyRankRow;
import com.java2nb.novel.service.gamification.MonthlySeasonRow;
import com.java2nb.novel.service.gamification.RewardCampaignCommand;
import com.java2nb.novel.service.gamification.RewardCampaignRow;
import com.java2nb.novel.service.gamification.RewardShareRule;
import com.java2nb.novel.service.wallet.WalletLedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.time.Duration;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthorRewardServiceImpl implements AuthorRewardService {

    private static final int MAX_REWARD_RANKS = 10;
    private static final int MAX_BATCH_SIZE = 500;
    private static final BigInteger BASIS_POINTS = BigInteger.valueOf(10_000L);

    private final AuthorRewardMapper authorRewardMapper;
    private final WalletLedgerService walletLedgerService;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public RewardCampaignRow calculateAllocations(RewardCampaignCommand command) {
        Objects.requireNonNull(command, "Thiếu yêu cầu tính quỹ thưởng");
        List<RewardShareRule> shares = validateShares(command);
        MonthlySeasonRow season = requireFinalizedSeason(command.seasonId());
        if (!Objects.equals(season.getPolicyVersion(), command.policyVersion())) {
            throw new IllegalArgumentException("Phiên bản chính sách thưởng không khớp kỳ xếp hạng");
        }
        String structureJson = canonicalStructure(shares);
        RewardCampaignRow existing = authorRewardMapper.selectCampaignByPeriod(season.getPeriodCode());
        if (existing != null) {
            validateExistingCampaign(existing, command, structureJson);
            return existing;
        }
        try {
            authorRewardMapper.insertCampaign(season.getPeriodCode(), command.budgetXu(),
                structureJson, command.policyVersion());
        } catch (DuplicateKeyException exception) {
            existing = authorRewardMapper.selectCampaignByPeriod(season.getPeriodCode());
            if (existing == null) {
                throw exception;
            }
            validateExistingCampaign(existing, command, structureJson);
            return existing;
        }
        RewardCampaignRow campaign = authorRewardMapper.selectCampaignByPeriod(season.getPeriodCode());
        if (campaign == null) {
            throw new IllegalStateException("Không đọc được campaign thưởng vừa tạo");
        }

        List<MonthlyRankRow> ranks = authorRewardMapper.selectSnapshotRewardRows(
            season.getSnapshotId(), shares.size());
        if (ranks.size() != shares.size()) {
            throw new IllegalStateException("Snapshot không có đủ số hạng theo cơ cấu thưởng");
        }
        long[] baseAmounts = new long[shares.size()];
        long allocatedBeforeRounding = 0;
        for (int index = 0; index < shares.size(); index++) {
            baseAmounts[index] = floorShare(command.budgetXu(), shares.get(index).basisPoints());
            allocatedBeforeRounding = Math.addExact(allocatedBeforeRounding, baseAmounts[index]);
        }
        long rounding = Math.subtractExact(command.budgetXu(), allocatedBeforeRounding);
        Set<Long> rewardedAuthors = new HashSet<>();
        for (int index = 0; index < ranks.size(); index++) {
            MonthlyRankRow rank = ranks.get(index);
            RewardShareRule share = shares.get(index);
            validateSnapshotRank(rank, share);
            boolean duplicateAuthor = !rewardedAuthors.add(rank.getAuthorId());
            long grossAmount = Math.addExact(baseAmounts[index], index == 0 ? rounding : 0);
            AuthorRewardAllocationRow allocation = new AuthorRewardAllocationRow();
            allocation.setAllocationNo(season.getPeriodCode() + ':' + rank.getBookId() + ':'
                + rank.getRankNo() + ':' + rank.getAuthorId());
            allocation.setCampaignId(campaign.getId());
            allocation.setSeasonId(season.getId());
            allocation.setSnapshotId(season.getSnapshotId());
            allocation.setBookId(rank.getBookId());
            allocation.setAuthorId(rank.getAuthorId());
            allocation.setRankNo(rank.getRankNo());
            allocation.setAmountXu(duplicateAuthor ? 0L : grossAmount);
            allocation.setRoundingAdjustmentXu(index == 0 ? rounding : 0L);
            allocation.setStatus(duplicateAuthor ? "SKIPPED_DUPLICATE_AUTHOR" : "CALCULATED");
            allocation.setReason(duplicateAuthor ? "Tác giả đã nhận giải cao hơn trong cùng kỳ" : null);
            allocation.setPolicyVersion(command.policyVersion());
            if (authorRewardMapper.insertAllocation(allocation) != 1) {
                throw new IllegalStateException("Không thể ghi phân bổ thưởng tác giả");
            }
        }
        return campaign;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public RewardCampaignRow approveCampaign(long campaignId, long approvedBy, Date approvedAt) {
        if (campaignId <= 0 || approvedBy <= 0 || approvedAt == null) {
            throw new IllegalArgumentException("Yêu cầu duyệt campaign thưởng không hợp lệ");
        }
        RewardCampaignRow campaign = authorRewardMapper.lockCampaignById(campaignId);
        if (campaign == null) {
            throw new IllegalArgumentException("Campaign thưởng không tồn tại");
        }
        if ("APPROVED".equals(campaign.getStatus())) {
            return campaign;
        }
        if (!"DRAFT".equals(campaign.getStatus())) {
            throw new IllegalStateException("Campaign không ở trạng thái có thể duyệt");
        }
        MonthlySeasonRow season = authorRewardMapper
            .selectSeasonByPeriodForReward(campaign.getPeriodCode());
        if (season == null || !"FINALIZED".equals(season.getStatus())
            || season.getSnapshotId() == null) {
            throw new IllegalStateException("Kỳ xếp hạng chưa sẵn sàng để duyệt thưởng");
        }
        if (Objects.equals(season.getFinalizedBy(), approvedBy)) {
            throw new SecurityException("Người chốt kỳ không được đồng thời duyệt quỹ thưởng");
        }
        if (authorRewardMapper.approveCampaign(campaignId, campaign.getVersion(), approvedBy,
            approvedAt) != 1) {
            throw new IllegalStateException("Campaign thưởng đã được cập nhật đồng thời");
        }
        authorRewardMapper.approveAllocations(campaignId);
        if (authorRewardMapper.attachCampaignToSeason(season.getId(), campaignId) != 1) {
            throw new IllegalStateException("Không thể gắn campaign thưởng vào kỳ xếp hạng");
        }
        RewardCampaignRow approved = authorRewardMapper.lockCampaignById(campaignId);
        if (approved == null || !"APPROVED".equals(approved.getStatus())) {
            throw new IllegalStateException("Không đọc được campaign thưởng vừa duyệt");
        }
        return approved;
    }

    @Override
    public List<Long> listApprovedAllocationIds(long campaignId, int limit) {
        if (campaignId <= 0) {
            throw new IllegalArgumentException("Mã campaign thưởng không hợp lệ");
        }
        return authorRewardMapper.selectApprovedAllocationIds(campaignId, safeLimit(limit));
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public AuthorRewardAllocationRow postPendingReward(long allocationId, Date postedAt) {
        AuthorRewardAllocationRow allocation = lockAllocation(allocationId);
        if ("POSTED_PENDING".equals(allocation.getStatus())) {
            return allocation;
        }
        if (!"APPROVED".equals(allocation.getStatus()) || allocation.getAmountXu() <= 0) {
            throw new IllegalStateException("Phân bổ chưa sẵn sàng để ghi thưởng pending");
        }
        Objects.requireNonNull(postedAt, "Thiếu thời điểm ghi thưởng pending");
        String key = "MONTHLY_AUTHOR_REWARD:" + allocation.getAllocationNo();
        walletLedgerService.creditAuthorRewardPending(allocation.getAuthorId(), allocation.getAmountXu(),
            allocation.getAllocationNo(), key, "Thưởng xếp hạng Ngọn Đuốc tháng");
        if (authorRewardMapper.markPostedPending(allocationId, allocation.getVersion(), postedAt) != 1) {
            throw new IllegalStateException("Phân bổ thưởng đã được cập nhật đồng thời");
        }
        return requireAllocation(allocationId);
    }

    @Override
    public List<Long> listMaturedAllocationIds(Date postedBefore, int limit) {
        Objects.requireNonNull(postedBefore, "Thiếu cutoff giải phóng thưởng");
        return authorRewardMapper.selectMaturedAllocationIds(postedBefore, safeLimit(limit));
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public AuthorRewardAllocationRow releaseMaturedReward(long allocationId, Date releasedAt) {
        AuthorRewardAllocationRow allocation = lockAllocation(allocationId);
        if ("RELEASED".equals(allocation.getStatus())) {
            return allocation;
        }
        if (!"POSTED_PENDING".equals(allocation.getStatus())) {
            throw new IllegalStateException("Phân bổ không ở trạng thái chờ giải phóng");
        }
        Objects.requireNonNull(releasedAt, "Thiếu thời điểm giải phóng thưởng");
        String key = "MONTHLY_AUTHOR_REWARD_RELEASE:" + allocation.getAllocationNo();
        walletLedgerService.releaseAuthorReward(allocation.getAuthorId(), allocation.getAmountXu(),
            allocation.getAllocationNo(), key, "Giải phóng thưởng xếp hạng Ngọn Đuốc");
        if (authorRewardMapper.markReleased(allocationId, allocation.getVersion(), releasedAt) != 1) {
            throw new IllegalStateException("Phân bổ thưởng đã được cập nhật đồng thời");
        }
        return requireAllocation(allocationId);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public AuthorRewardAllocationRow clawback(long allocationId, long operatorId, String reason,
                                              Date clawedBackAt, int claimWindowDays) {
        if (operatorId <= 0 || clawedBackAt == null || claimWindowDays <= 0) {
            throw new IllegalArgumentException("Yêu cầu thu hồi thưởng không hợp lệ");
        }
        String normalizedReason = normalizeReason(reason);
        AuthorRewardAllocationRow allocation = lockAllocation(allocationId);
        if ("CLAWED_BACK".equals(allocation.getStatus())) {
            return allocation;
        }
        if (!"POSTED_PENDING".equals(allocation.getStatus()) || allocation.getPostedAt() == null) {
            throw new IllegalStateException("Chỉ được thu hồi thưởng đang trong cửa sổ khiếu nại");
        }
        Date claimDeadline = Date.from(allocation.getPostedAt().toInstant()
            .plus(Duration.ofDays(claimWindowDays)));
        if (clawedBackAt.after(claimDeadline)) {
            throw new IllegalStateException("Đã hết cửa sổ thu hồi thưởng tự động");
        }
        String originalKey = "MONTHLY_AUTHOR_REWARD:" + allocation.getAllocationNo();
        String clawbackKey = "MONTHLY_AUTHOR_REWARD_CLAWBACK:" + allocation.getAllocationNo();
        walletLedgerService.reverseTransaction(originalKey, "MONTHLY_AUTHOR_REWARD_CLAWBACK",
            allocation.getAllocationNo(), clawbackKey, normalizedReason);
        String auditReason = "ADMIN:" + operatorId + ':' + normalizedReason;
        if (authorRewardMapper.markClawedBack(allocationId, allocation.getVersion(), clawedBackAt,
            auditReason) != 1) {
            throw new IllegalStateException("Phân bổ thưởng đã được cập nhật đồng thời");
        }
        return requireAllocation(allocationId);
    }

    @Override
    public List<AuthorRewardAllocationRow> listAuthorRewards(long authorId, int limit) {
        if (authorId <= 0) {
            throw new IllegalArgumentException("Mã tác giả không hợp lệ");
        }
        return authorRewardMapper.selectAuthorRewards(authorId, safeLimit(limit));
    }

    private List<RewardShareRule> validateShares(RewardCampaignCommand command) {
        if (command.seasonId() <= 0 || command.budgetXu() <= 0
            || command.policyVersion() == null || command.policyVersion().isBlank()) {
            throw new IllegalArgumentException("Cấu hình campaign thưởng không hợp lệ");
        }
        List<RewardShareRule> shares = command.shares();
        if (shares.isEmpty() || shares.size() > MAX_REWARD_RANKS) {
            throw new IllegalArgumentException("Cơ cấu thưởng phải có từ 1 đến 10 hạng");
        }
        int total = 0;
        for (int index = 0; index < shares.size(); index++) {
            RewardShareRule share = shares.get(index);
            if (share == null || share.rank() != index + 1 || share.basisPoints() <= 0) {
                throw new IllegalArgumentException("Cơ cấu thưởng phải liên tục từ hạng 1");
            }
            total = Math.addExact(total, share.basisPoints());
        }
        if (total != 10_000) {
            throw new IllegalArgumentException("Tổng tỷ lệ cơ cấu thưởng phải bằng 10.000 basis point");
        }
        return shares;
    }

    private MonthlySeasonRow requireFinalizedSeason(long seasonId) {
        MonthlySeasonRow season = authorRewardMapper.selectSeasonForReward(seasonId);
        if (season == null || !"FINALIZED".equals(season.getStatus())
            || season.getSnapshotId() == null) {
            throw new IllegalStateException("Chỉ được tính thưởng từ kỳ FINALIZED có snapshot");
        }
        return season;
    }

    private long floorShare(long budgetXu, int basisPoints) {
        return BigInteger.valueOf(budgetXu).multiply(BigInteger.valueOf(basisPoints))
            .divide(BASIS_POINTS).longValueExact();
    }

    private void validateSnapshotRank(MonthlyRankRow row, RewardShareRule share) {
        if (row == null || row.getRankNo() == null || row.getRankNo() != share.rank()
            || row.getBookId() == null || row.getBookId() <= 0
            || row.getAuthorId() == null || row.getAuthorId() <= 0) {
            throw new IllegalStateException("Dòng snapshot không hợp lệ để tính thưởng");
        }
    }

    private String canonicalStructure(List<RewardShareRule> shares) {
        StringBuilder json = new StringBuilder("{\"sharesBps\":[");
        for (int index = 0; index < shares.size(); index++) {
            if (index > 0) {
                json.append(',');
            }
            RewardShareRule share = shares.get(index);
            json.append("{\"rank\":").append(share.rank())
                .append(",\"basisPoints\":").append(share.basisPoints()).append('}');
        }
        return json.append("]}").toString();
    }

    private void validateExistingCampaign(RewardCampaignRow existing, RewardCampaignCommand command,
                                          String structureJson) {
        if (!Objects.equals(existing.getBudgetXu(), command.budgetXu())
            || !Objects.equals(existing.getStructureJson(), structureJson)
            || !Objects.equals(existing.getPolicyVersion(), command.policyVersion())) {
            throw new IllegalStateException("Campaign của kỳ đã tồn tại với cấu hình khác");
        }
    }

    private AuthorRewardAllocationRow lockAllocation(long allocationId) {
        if (allocationId <= 0) {
            throw new IllegalArgumentException("Mã phân bổ thưởng không hợp lệ");
        }
        AuthorRewardAllocationRow row = authorRewardMapper.lockAllocationById(allocationId);
        if (row == null) {
            throw new IllegalArgumentException("Phân bổ thưởng không tồn tại");
        }
        return row;
    }

    private AuthorRewardAllocationRow requireAllocation(long allocationId) {
        AuthorRewardAllocationRow row = authorRewardMapper.selectAllocationById(allocationId);
        if (row == null) {
            throw new IllegalStateException("Không đọc được phân bổ thưởng vừa cập nhật");
        }
        return row;
    }

    private String normalizeReason(String reason) {
        String normalized = reason == null ? "" : reason.trim();
        if (normalized.length() < 10 || normalized.length() > 255) {
            throw new IllegalArgumentException("Lý do thu hồi phải dài từ 10 đến 255 ký tự");
        }
        return normalized;
    }

    private int safeLimit(int limit) {
        return Math.max(1, Math.min(limit, MAX_BATCH_SIZE));
    }
}
