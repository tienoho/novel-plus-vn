package com.java2nb.novel.service.impl;

import com.java2nb.novel.mapper.GamificationProgressMapper;
import com.java2nb.novel.mapper.MonthlyTicketMapper;
import com.java2nb.novel.service.gamification.GamificationEventRow;
import com.java2nb.novel.service.gamification.LevelRewardGrantRow;
import com.java2nb.novel.service.gamification.LevelRewardPolicyRow;
import com.java2nb.novel.service.gamification.LevelRewardService;
import com.java2nb.novel.service.gamification.MonthlyTicketService;
import com.java2nb.novel.service.gamification.TicketGrantCommand;
import com.java2nb.novel.service.gamification.TicketLedgerRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class LevelRewardServiceImpl implements LevelRewardService {

    private static final Pattern LEVEL_PAYLOAD = Pattern.compile("\\{\\s*\"level\"\\s*:\\s*(\\d+)\\s*}");

    private final GamificationProgressMapper mapper;
    private final MonthlyTicketService monthlyTicketService;
    private final MonthlyTicketMapper monthlyTicketMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int apply(GamificationEventRow event) {
        if (event == null || !"LEVEL_REACHED".equals(event.getEventType())) {
            return 0;
        }
        int level = validateAndReadLevel(event);
        LevelRewardPolicyRow policy = mapper.selectLevelRewardPolicy(event.getPolicyVersion(), level);
        if (policy == null) {
            return 0;
        }
        validatePolicy(policy, event.getPolicyVersion(), level);
        LevelRewardGrantRow existing = mapper.selectLevelRewardGrant(
            event.getUserId(), level, event.getPolicyVersion());
        if (existing != null) {
            validateExisting(existing, event.getUserId(), policy);
            return 1;
        }

        String idempotencyKey = "LEVEL_UP:" + event.getUserId() + ':' + level + ':'
            + event.getPolicyVersion();
        Date effectiveAt = event.getOccurredAt();
        Date expireAt = Date.from(effectiveAt.toInstant().plus(
            policy.getTicketValidityDays(), ChronoUnit.DAYS));
        monthlyTicketService.grant(new TicketGrantCommand(event.getUserId(),
            policy.getTicketAmount(), "LEVEL_UP", "LEVEL:" + level, idempotencyKey,
            effectiveAt, expireAt, "SYSTEM", null, null, event.getPolicyVersion(),
            event.getRuntimeConfigRevision()));
        TicketLedgerRow ledger = monthlyTicketMapper.selectLedgerByIdempotencyKey(idempotencyKey);
        if (ledger == null) {
            throw new IllegalStateException("Không đọc được bút toán Đuốc thưởng level");
        }
        mapper.insertLevelRewardGrantIgnore(event.getId(), event.getUserId(), level,
            event.getPolicyVersion(), policy.getTicketAmount(), ledger.getId(), idempotencyKey);
        LevelRewardGrantRow grant = mapper.selectLevelRewardGrant(
            event.getUserId(), level, event.getPolicyVersion());
        if (grant == null) {
            throw new IllegalStateException("Không ghi được kết quả thưởng level");
        }
        validateExisting(grant, event.getUserId(), policy);
        return 1;
    }

    private int validateAndReadLevel(GamificationEventRow event) {
        if (event.getId() == null || event.getId() <= 0 || event.getUserId() == null
            || event.getUserId() <= 0 || event.getOccurredAt() == null
            || event.getPolicyVersion() == null || event.getPolicyVersion().isBlank()
            || event.getRuntimeConfigRevision() == null
            || event.getRuntimeConfigRevision() <= 0) {
            throw new IllegalStateException("Event level-up thiếu dữ liệu bắt buộc");
        }
        Matcher matcher = LEVEL_PAYLOAD.matcher(Objects.toString(event.getPayloadJson(), ""));
        if (!matcher.matches()) {
            throw new IllegalStateException("Payload event level-up không hợp lệ");
        }
        int level = Integer.parseInt(matcher.group(1));
        String prefix = "GAMIFY:LEVEL_REACHED:" + event.getUserId() + ':' + level + ':';
        if (level <= 1 || event.getSourceKey() == null || !event.getSourceKey().startsWith(prefix)) {
            throw new IllegalStateException("Danh tính event level-up không khớp payload");
        }
        return level;
    }

    private void validatePolicy(LevelRewardPolicyRow policy, String policyVersion, int level) {
        if (!Objects.equals(policyVersion, policy.getPolicyVersion())
            || !Objects.equals(level, policy.getLevel()) || policy.getTicketAmount() == null
            || policy.getTicketAmount() <= 0 || policy.getTicketValidityDays() == null
            || policy.getTicketValidityDays() <= 0) {
            throw new IllegalStateException("Cấu hình thưởng level không hợp lệ");
        }
    }

    private void validateExisting(LevelRewardGrantRow grant, long userId,
                                  LevelRewardPolicyRow policy) {
        String expectedKey = "LEVEL_UP:" + userId + ':' + policy.getLevel() + ':'
            + policy.getPolicyVersion();
        if (!Objects.equals(grant.getUserId(), userId)
            || !Objects.equals(grant.getLevel(), policy.getLevel())
            || !Objects.equals(grant.getPolicyVersion(), policy.getPolicyVersion())
            || !Objects.equals(grant.getTicketAmount(), policy.getTicketAmount())
            || !Objects.equals(grant.getIdempotencyKey(), expectedKey)
            || grant.getTicketLedgerId() == null) {
            throw new IllegalStateException("Kết quả thưởng level đã tồn tại với nội dung khác");
        }
    }
}
