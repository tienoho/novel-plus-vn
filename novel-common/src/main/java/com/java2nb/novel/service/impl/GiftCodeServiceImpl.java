package com.java2nb.novel.service.impl;

import com.java2nb.novel.mapper.GiftCodeMapper;
import com.java2nb.novel.service.entitlement.ReadingTicketGrantCommand;
import com.java2nb.novel.service.entitlement.ReadingTicketService;
import com.java2nb.novel.service.gift.GiftCodeClaimRow;
import com.java2nb.novel.service.gift.GiftCodeHashCandidate;
import com.java2nb.novel.service.gift.GiftCodePage;
import com.java2nb.novel.service.gift.GiftCampaignCommand;
import com.java2nb.novel.service.gift.GiftCampaignRow;
import com.java2nb.novel.service.gift.GiftCodeProperties;
import com.java2nb.novel.service.gift.GiftCodeRedeemException;
import com.java2nb.novel.service.gift.GiftCodeRow;
import com.java2nb.novel.service.gift.GiftCodeService;
import com.java2nb.novel.service.gift.GiftRedeemCommand;
import com.java2nb.novel.service.gift.GiftRedemptionInsert;
import com.java2nb.novel.service.gift.GiftRedemptionHistoryPage;
import com.java2nb.novel.service.gift.GiftRedemptionResult;
import com.java2nb.novel.service.gift.GiftRedemptionRow;
import com.java2nb.novel.service.gift.GiftRedemptionStatus;
import com.java2nb.novel.service.wallet.WalletLedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Date;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GiftCodeServiceImpl implements GiftCodeService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final char[] CODE_ALPHABET =
        "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private final GiftCodeMapper mapper;
    private final WalletLedgerService walletService;
    private final ReadingTicketService ticketService;
    private final GiftCodeProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional(readOnly = true)
    public List<GiftCampaignRow> listCampaigns() {
        return mapper.selectCampaigns();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GiftCampaignRow createCampaign(GiftCampaignCommand command) {
        if (command == null || properties.getPolicyVersion() == null
            || properties.getPolicyVersion().isBlank()) {
            throw new IllegalArgumentException("Thiếu cấu hình chiến dịch mã quà");
        }
        if (mapper.insertCampaign(command, properties.getPolicyVersion()) != 1) {
            throw new IllegalStateException("Không thể tạo chiến dịch mã quà");
        }
        GiftCampaignRow created = mapper.selectCampaignByCode(command.campaignCode());
        if (created == null) {
            throw new IllegalStateException("Không đọc được chiến dịch mã quà vừa tạo");
        }
        return created;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GiftCampaignRow changeCampaignStatus(long campaignId, long expectedVersion,
                                                String status) {
        String target = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!"ACTIVE".equals(target) && !"CLOSED".equals(target)) {
            throw new IllegalArgumentException("Trạng thái chiến dịch mã quà không hợp lệ");
        }
        GiftCampaignRow current = requireCampaign(campaignId);
        if (!java.util.Objects.equals(current.getVersion(), expectedVersion)) {
            throw new IllegalStateException("Chiến dịch mã quà đã được cập nhật đồng thời");
        }
        if ("CLOSED".equals(current.getStatus())) {
            throw new IllegalStateException("Chiến dịch mã quà CLOSED là trạng thái kết thúc");
        }
        if (java.util.Objects.equals(current.getStatus(), target)) {
            return current;
        }
        if ("ACTIVE".equals(target) && !properties.isReady()) {
            throw new IllegalStateException("Không thể kích hoạt khi mã quà chưa được cấu hình an toàn");
        }
        if (mapper.updateCampaignStatus(campaignId, expectedVersion, target) != 1) {
            throw new IllegalStateException("Chiến dịch mã quà đã được cập nhật đồng thời");
        }
        return requireCampaign(campaignId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<String> issueCodes(long campaignId, int quantity, long maxRedemptionsPerCode) {
        if (quantity < 1 || quantity > 1000 || maxRedemptionsPerCode < 1
            || !properties.hasValidKeyRing()) {
            throw new IllegalArgumentException("Yêu cầu phát hành mã quà không hợp lệ");
        }
        GiftCampaignRow campaign = requireCampaign(campaignId);
        if ("CLOSED".equals(campaign.getStatus())
            || maxRedemptionsPerCode > campaign.getMaxRedemptions()) {
            throw new IllegalStateException("Chiến dịch không cho phép phát hành mã quà này");
        }
        List<String> plaintextCodes = new ArrayList<>(quantity);
        for (int index = 0; index < quantity; index++) {
            plaintextCodes.add(issueOneCode(campaignId, maxRedemptionsPerCode));
        }
        return List.copyOf(plaintextCodes);
    }

    @Override
    @Transactional(readOnly = true)
    public GiftCodePage listCodes(long campaignId, int page, int pageSize) {
        requireCampaign(campaignId);
        int safePage = safePage(page);
        int safePageSize = safePageSize(pageSize);
        long offset = pageOffset(safePage, safePageSize);
        return new GiftCodePage(mapper.selectCodesByCampaign(campaignId, offset, safePageSize),
            mapper.countCodesByCampaign(campaignId), safePage, safePageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GiftCodeRow revokeCode(long codeId, long expectedVersion) {
        if (codeId <= 0 || expectedVersion < 0) {
            throw new IllegalArgumentException("Mã quà hoặc phiên bản không hợp lệ");
        }
        if (mapper.revokeUnusedCode(codeId, expectedVersion) != 1) {
            throw new IllegalStateException(
                "Mã quà đã được dùng, thu hồi hoặc cập nhật đồng thời");
        }
        GiftCodeRow revoked = mapper.selectCodeById(codeId);
        if (revoked == null || !"REVOKED".equals(revoked.getStatus())) {
            throw new IllegalStateException("Không đọc được mã quà vừa thu hồi");
        }
        return revoked;
    }

    @Override
    @Transactional(readOnly = true)
    public GiftRedemptionHistoryPage listUserRedemptions(long userId, int page, int pageSize) {
        if (userId <= 0) {
            throw new IllegalArgumentException("Người dùng không hợp lệ");
        }
        int safePage = safePage(page);
        int safePageSize = safePageSize(pageSize);
        long offset = pageOffset(safePage, safePageSize);
        return new GiftRedemptionHistoryPage(
            mapper.selectRedemptionsByUser(userId, offset, safePageSize),
            mapper.countRedemptionsByUser(userId), safePage, safePageSize);
    }

    @Override
    @Transactional(readOnly = true)
    public GiftRedemptionHistoryPage listCampaignRedemptions(long campaignId, int page,
                                                             int pageSize) {
        requireCampaign(campaignId);
        int safePage = safePage(page);
        int safePageSize = safePageSize(pageSize);
        long offset = pageOffset(safePage, safePageSize);
        return new GiftRedemptionHistoryPage(
            mapper.selectRedemptionsByCampaign(campaignId, offset, safePageSize),
            mapper.countRedemptionsByCampaign(campaignId), safePage, safePageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GiftRedemptionResult redeem(GiftRedeemCommand command) {
        if (!properties.isReady()) {
            throw new GiftCodeRedeemException("Tính năng mã quà đang tắt hoặc cấu hình chưa hợp lệ");
        }
        GiftCodeClaimRow claim = mapper.lockClaimByHashes(hashCandidates(command.code()));
        if (claim == null) {
            throw new GiftCodeRedeemException("Mã quà không hợp lệ");
        }
        GiftRedemptionRow existing = mapper.selectRedemptionByCodeUser(
            claim.getCodeId(), command.userId());
        if (existing != null) {
            return GiftRedemptionResult.already(existing);
        }
        validateClaim(claim, command);
        if (mapper.countCampaignUserRedemptions(claim.getCampaignId(), command.userId())
            >= claim.getMaxPerUser()) {
            throw new GiftCodeRedeemException("Bạn đã đạt giới hạn nhận quà của chiến dịch");
        }

        String idempotencyKey = "GIFT:" + claim.getCodeId() + ':' + command.userId();
        GiftRedemptionInsert insert = new GiftRedemptionInsert(claim.getCampaignId(),
            claim.getCodeId(), command.userId(), claim.getRewardType(), claim.getRewardAmount(),
            command.clientRequestId(), command.occurredAt(), properties.getPolicyVersion());
        try {
            postReward(claim, command, idempotencyKey);
            int inserted = "XU".equals(claim.getRewardType())
                ? mapper.insertWalletRedemption(insert, idempotencyKey)
                : mapper.insertTicketRedemption(insert, idempotencyKey);
            if (inserted != 1
                || mapper.incrementCodeRedemption(claim.getCodeId(),
                    claim.getCodeMaxRedemptions()) != 1
                || mapper.incrementCampaignRedemption(claim.getCampaignId(),
                    claim.getCampaignMaxRedemptions()) != 1) {
                throw new IllegalStateException("Không thể hoàn tất biên nhận mã quà");
            }
        } catch (DuplicateKeyException exception) {
            existing = mapper.selectRedemptionByCodeUserCurrent(
                claim.getCodeId(), command.userId());
            if (existing != null) {
                return GiftRedemptionResult.already(existing);
            }
            GiftRedemptionRow requestCollision = mapper.selectRedemptionByUserRequestCurrent(
                command.userId(), command.clientRequestId());
            if (requestCollision != null) {
                throw new GiftCodeRedeemException("Yêu cầu đổi mã quà đã được sử dụng");
            }
            throw exception;
        }
        GiftRedemptionRow posted = mapper.selectRedemptionByCodeUserCurrent(
            claim.getCodeId(), command.userId());
        if (posted == null) {
            throw new IllegalStateException("Không đọc được biên nhận mã quà vừa tạo");
        }
        return new GiftRedemptionResult(GiftRedemptionStatus.POSTED, posted.getId(),
            posted.getRewardType(), posted.getRewardAmount());
    }

    private void validateClaim(GiftCodeClaimRow claim, GiftRedeemCommand command) {
        Date now = command.occurredAt();
        if (!"ACTIVE".equals(claim.getCampaignStatus())
            || !"ACTIVE".equals(claim.getCodeStatus())
            || now.before(claim.getStartAt()) || !now.before(claim.getEndAt())
            || claim.getCodeRedeemedCount() >= claim.getCodeMaxRedemptions()
            || claim.getCampaignRedeemedCount() >= claim.getCampaignMaxRedemptions()) {
            throw new GiftCodeRedeemException("Mã quà đã hết hạn, hết lượt hoặc chưa được kích hoạt");
        }
        if (!"XU".equals(claim.getRewardType())
            && !"READING_TICKET".equals(claim.getRewardType())) {
            throw new IllegalStateException("Loại phần thưởng mã quà không được hỗ trợ");
        }
    }

    private void postReward(GiftCodeClaimRow claim, GiftRedeemCommand command,
                            String idempotencyKey) {
        if ("XU".equals(claim.getRewardType())) {
            walletService.creditReaderReward(command.userId(), claim.getRewardAmount(),
                Long.toString(claim.getCodeId()), idempotencyKey, "Nhận Xu từ mã quà");
            return;
        }
        Date expireAt = Date.from(command.occurredAt().toInstant().plus(
            Duration.ofDays(claim.getTicketValidityDays())));
        ticketService.grant(new ReadingTicketGrantCommand(command.userId(),
            claim.getRewardAmount(), "GIFT_CODE", "GIFT:" + claim.getCodeId(),
            idempotencyKey, command.occurredAt(), expireAt, "SYSTEM", null,
            "Nhận Vé đọc từ mã quà", properties.getPolicyVersion()));
    }

    private String hmac(String code, byte[] secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(code.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Không thể băm mã quà", exception);
        }
    }

    private GiftCampaignRow requireCampaign(long campaignId) {
        if (campaignId <= 0) {
            throw new IllegalArgumentException("Mã chiến dịch không hợp lệ");
        }
        GiftCampaignRow row = mapper.selectCampaignById(campaignId);
        if (row == null) {
            throw new IllegalStateException("Chiến dịch mã quà không tồn tại");
        }
        return row;
    }

    private String issueOneCode(long campaignId, long maxRedemptionsPerCode) {
        for (int attempt = 0; attempt < 5; attempt++) {
            String plaintext = randomCode();
            String normalized = plaintext.replace("-", "");
            List<GiftCodeHashCandidate> candidates = hashCandidates(normalized);
            if (mapper.countCodesByHashes(candidates) > 0) {
                continue;
            }
            GiftCodeHashCandidate active = candidates.stream()
                .filter(candidate -> properties.getHmacKeyId().equals(candidate.keyId()))
                .findFirst().orElseThrow(() -> new IllegalStateException(
                    "Thiếu khóa HMAC active trong key ring mã quà"));
            try {
                if (mapper.insertCode(campaignId, active.keyId(), active.codeHash(),
                    plaintext.substring(plaintext.length() - 4), maxRedemptionsPerCode) == 1) {
                    return plaintext;
                }
            } catch (DuplicateKeyException ignored) {
                // Xác suất trùng cực thấp; sinh mã mới, tuyệt đối không tái sử dụng plaintext.
            }
        }
        throw new IllegalStateException("Không thể phát hành mã quà duy nhất");
    }

    private List<GiftCodeHashCandidate> hashCandidates(String normalizedCode) {
        List<GiftCodeHashCandidate> candidates = new ArrayList<>();
        for (Map.Entry<String, byte[]> key : properties.configuredKeyBytes().entrySet()) {
            candidates.add(new GiftCodeHashCandidate(
                key.getKey(), hmac(normalizedCode, key.getValue())));
        }
        return List.copyOf(candidates);
    }

    private String randomCode() {
        StringBuilder value = new StringBuilder(24);
        for (int index = 0; index < 20; index++) {
            if (index > 0 && index % 4 == 0) {
                value.append('-');
            }
            value.append(CODE_ALPHABET[secureRandom.nextInt(CODE_ALPHABET.length)]);
        }
        return value.toString();
    }

    private int safePage(int page) {
        return Math.max(1, page);
    }

    private int safePageSize(int pageSize) {
        return Math.max(1, Math.min(pageSize, MAX_PAGE_SIZE));
    }

    private long pageOffset(int page, int pageSize) {
        return Math.multiplyExact((long) page - 1, pageSize);
    }
}
