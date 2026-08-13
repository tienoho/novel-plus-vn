package com.java2nb.novel.service.impl;

import com.java2nb.novel.core.enums.ResponseStatus;
import com.java2nb.novel.core.exception.BusinessException;
import com.java2nb.novel.mapper.TicketRiskMapper;
import com.java2nb.novel.service.gamification.TicketRiskAssessmentRow;
import com.java2nb.novel.service.gamification.TicketRiskCommand;
import com.java2nb.novel.service.gamification.TicketRiskDecision;
import com.java2nb.novel.service.gamification.TicketRiskPolicyRow;
import com.java2nb.novel.service.gamification.TicketRiskRuleRow;
import com.java2nb.novel.service.gamification.TicketRiskReviewRow;
import com.java2nb.novel.service.gamification.TicketRiskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class TicketRiskServiceImpl implements TicketRiskService {

    private final TicketRiskMapper mapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public TicketRiskDecision assess(TicketRiskCommand command) {
        Objects.requireNonNull(command, "Thiếu yêu cầu đánh giá rủi ro");
        TicketRiskAssessmentRow existing = mapper.selectAssessment(
            command.userId(), command.clientRequestId());
        if (existing != null) {
            validateReplay(existing, command);
            return toDecision(existing);
        }

        TicketRiskPolicyRow policy = mapper.selectPolicy(command.policyVersion());
        List<TicketRiskRuleRow> rules = policy == null ? List.of()
            : mapper.selectRules(command.policyVersion());
        Evaluation evaluation = evaluate(command, rules);
        int reviewThreshold = policy == null ? Integer.MAX_VALUE
            : requireReviewThreshold(policy, command.policyVersion());
        String action = evaluation.hardBlock() ? "BLOCK"
            : evaluation.score() >= reviewThreshold ? "REVIEW" : "ALLOW";
        String matchedRules = String.join(",", evaluation.matchedRules());
        if (matchedRules.length() > 512) {
            throw new IllegalStateException("Quá nhiều rule chống lạm dụng cùng khớp");
        }
        mapper.insertAssessmentIgnore(command, evaluation.score(), action, matchedRules);
        TicketRiskAssessmentRow assessment = mapper.selectAssessment(
            command.userId(), command.clientRequestId());
        if (assessment == null) {
            throw new IllegalStateException("Không ghi được đánh giá rủi ro thắp Đuốc");
        }
        validateReplay(assessment, command);
        if (!"ALLOW".equals(assessment.getAction())) {
            mapper.insertReviewIgnore(assessment.getId());
        }
        return toDecision(assessment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TicketRiskReviewRow review(long assessmentId, long expectedVersion, String decision,
                                      long operatorId, String reason, Date reviewedAt) {
        if (assessmentId <= 0 || expectedVersion < 0 || operatorId <= 0 || reviewedAt == null
            || !("APPROVED".equals(decision) || "REJECTED".equals(decision))) {
            throw new IllegalArgumentException("Yêu cầu review rủi ro không hợp lệ");
        }
        String normalizedReason = reason == null ? "" : reason.trim();
        if (normalizedReason.length() < 10 || normalizedReason.length() > 500) {
            throw new IllegalArgumentException("Lý do review phải dài từ 10 đến 500 ký tự");
        }
        TicketRiskReviewRow current = mapper.lockReview(assessmentId);
        if (current == null || !"PENDING".equals(current.getStatus())
            || !Objects.equals(current.getVersion(), expectedVersion)) {
            throw new IllegalStateException("Risk review đã được xử lý đồng thời hoặc không tồn tại");
        }
        if (mapper.updateReview(assessmentId, expectedVersion, decision, operatorId,
            normalizedReason, reviewedAt) != 1) {
            throw new IllegalStateException("Không thể cập nhật risk review");
        }
        if (mapper.insertReviewAudit(assessmentId, "PENDING", decision, operatorId,
            normalizedReason) != 1) {
            throw new IllegalStateException("Không thể ghi audit risk review");
        }
        TicketRiskReviewRow updated = mapper.selectReview(assessmentId);
        if (updated == null) {
            throw new IllegalStateException("Không đọc được risk review sau cập nhật");
        }
        return updated;
    }

    private Evaluation evaluate(TicketRiskCommand command, List<TicketRiskRuleRow> rules) {
        int score = 0;
        boolean hardBlock = false;
        List<String> matched = new ArrayList<>();
        for (TicketRiskRuleRow rule : rules) {
            validateRule(rule);
            long value = metric(command, rule);
            boolean hit = "ACCOUNT_AGE_HOURS".equals(rule.getMetricName())
                ? value < rule.getThresholdValue() : value >= rule.getThresholdValue();
            if (hit) {
                score = Math.addExact(score, rule.getScore());
                hardBlock |= Boolean.TRUE.equals(rule.getHardBlock());
                matched.add(rule.getRuleCode());
            }
        }
        return new Evaluation(score, hardBlock, matched);
    }

    private long metric(TicketRiskCommand command, TicketRiskRuleRow rule) {
        if ("ACCOUNT_AGE_HOURS".equals(rule.getMetricName())) {
            Date createdAt = mapper.selectUserCreatedAt(command.userId());
            if (createdAt == null) {
                throw new BusinessException(ResponseStatus.GAMIFICATION_ACCOUNT_UNAVAILABLE);
            }
            return Math.max(0, Duration.between(createdAt.toInstant(),
                command.occurredAt().toInstant()).toHours());
        }
        Date since = Date.from(command.occurredAt().toInstant().minus(
            Duration.ofMinutes(rule.getWindowMinutes())));
        return switch (rule.getMetricName()) {
            case "USER_VOTES" -> mapper.countVotesByUserSince(command.userId(), since);
            case "DEVICE_VOTES" -> mapper.countVotesByDeviceSince(command.deviceHash(), since);
            case "DEVICE_USERS" -> mapper.countUsersByDeviceSince(command.deviceHash(), since);
            case "IP_VOTES" -> mapper.countVotesByIpSince(command.ipHash(), since);
            case "IP_USERS" -> mapper.countUsersByIpSince(command.ipHash(), since);
            default -> throw new IllegalStateException("Metric chống lạm dụng không được hỗ trợ");
        };
    }

    private void validateRule(TicketRiskRuleRow rule) {
        boolean accountAge = "ACCOUNT_AGE_HOURS".equals(rule.getMetricName());
        if (rule.getRuleCode() == null || !rule.getRuleCode().matches("[A-Z][A-Z0-9_]{2,47}")
            || rule.getThresholdValue() == null || rule.getThresholdValue() < 0
            || rule.getScore() == null || rule.getScore() <= 0
            || (!accountAge && (rule.getWindowMinutes() == null || rule.getWindowMinutes() <= 0))) {
            throw new IllegalStateException("Rule chống lạm dụng không hợp lệ");
        }
    }

    private int requireReviewThreshold(TicketRiskPolicyRow policy, String expectedVersion) {
        if (!Objects.equals(policy.getPolicyVersion(), expectedVersion)
            || policy.getReviewScoreThreshold() == null || policy.getReviewScoreThreshold() <= 0) {
            throw new IllegalStateException("Policy chống lạm dụng không hợp lệ");
        }
        return policy.getReviewScoreThreshold();
    }

    private void validateReplay(TicketRiskAssessmentRow row, TicketRiskCommand command) {
        if (!Objects.equals(row.getUserId(), command.userId())
            || !Objects.equals(row.getSeasonId(), command.seasonId())
            || !Objects.equals(row.getBookId(), command.bookId())
            || !Objects.equals(row.getDeviceHash(), command.deviceHash())
            || !Objects.equals(row.getIpHash(), command.ipHash())
            || !Objects.equals(row.getPolicyVersion(), command.policyVersion())) {
            throw new BusinessException(ResponseStatus.GAMIFICATION_IDEMPOTENCY_CONFLICT);
        }
    }

    private TicketRiskDecision toDecision(TicketRiskAssessmentRow row) {
        return new TicketRiskDecision(row.getId(), row.getRiskScore(), row.getAction(),
            row.getMatchedRules());
    }

    private record Evaluation(int score, boolean hardBlock, List<String> matchedRules) {
    }
}
