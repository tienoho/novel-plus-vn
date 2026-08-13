package com.java2nb.novel.service.gamification;

import com.java2nb.novel.core.exception.BusinessException;
import com.java2nb.novel.mapper.TicketRiskMapper;
import com.java2nb.novel.service.impl.TicketRiskServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TicketRiskServiceImplTest {

    private static final Date NOW = Date.from(Instant.parse("2026-08-01T03:00:00Z"));
    private static final String DEVICE_HASH = "d".repeat(64);
    private static final String IP_HASH = "e".repeat(64);

    private TicketRiskMapper mapper;
    private TicketRiskServiceImpl service;
    private TicketRiskCommand command;

    @BeforeEach
    void setUp() {
        mapper = mock(TicketRiskMapper.class);
        service = new TicketRiskServiceImpl(mapper);
        command = new TicketRiskCommand(11L, 21L, 31L, "risk-request-1",
            DEVICE_HASH, IP_HASH, NOW, "v1");
    }

    @Test
    void missingPolicyAllowsButStillRecordsImmutableAssessment() {
        TicketRiskAssessmentRow stored = assessment("ALLOW", 0, "");
        when(mapper.selectAssessment(11L, "risk-request-1")).thenReturn(null, stored);

        TicketRiskDecision decision = service.assess(command);

        assertThat(decision.action()).isEqualTo("ALLOW");
        verify(mapper).insertAssessmentIgnore(command, 0, "ALLOW", "");
        verify(mapper, never()).insertReviewIgnore(91L);
    }

    @Test
    void scoreThresholdCreatesReviewQueueItem() {
        TicketRiskPolicyRow policy = new TicketRiskPolicyRow();
        policy.setPolicyVersion("v1");
        policy.setReviewScoreThreshold(5);
        TicketRiskRuleRow youngAccount = rule("YOUNG_ACCOUNT", "ACCOUNT_AGE_HOURS", 24, null, 3, false);
        TicketRiskRuleRow sharedDevice = rule("SHARED_DEVICE", "DEVICE_USERS", 2, 60, 4, false);
        when(mapper.selectPolicy("v1")).thenReturn(policy);
        when(mapper.selectRules("v1")).thenReturn(List.of(youngAccount, sharedDevice));
        when(mapper.selectUserCreatedAt(11L))
            .thenReturn(Date.from(NOW.toInstant().minusSeconds(3_600)));
        when(mapper.countUsersByDeviceSince(DEVICE_HASH,
            Date.from(NOW.toInstant().minusSeconds(3_600)))).thenReturn(2L);
        TicketRiskAssessmentRow stored = assessment("REVIEW", 7, "YOUNG_ACCOUNT,SHARED_DEVICE");
        when(mapper.selectAssessment(11L, "risk-request-1")).thenReturn(null, stored);

        TicketRiskDecision decision = service.assess(command);

        assertThat(decision.score()).isEqualTo(7);
        assertThat(decision.action()).isEqualTo("REVIEW");
        verify(mapper).insertReviewIgnore(91L);
    }

    @Test
    void hardBlockOnlyOccursWhenMatchedRuleExplicitlyEnablesIt() {
        TicketRiskPolicyRow policy = new TicketRiskPolicyRow();
        policy.setPolicyVersion("v1");
        policy.setReviewScoreThreshold(100);
        TicketRiskRuleRow rule = rule("IP_HARD_BLOCK", "IP_USERS", 5, 60, 1, true);
        when(mapper.selectPolicy("v1")).thenReturn(policy);
        when(mapper.selectRules("v1")).thenReturn(List.of(rule));
        when(mapper.countUsersByIpSince(IP_HASH,
            Date.from(NOW.toInstant().minusSeconds(3_600)))).thenReturn(5L);
        TicketRiskAssessmentRow stored = assessment("BLOCK", 1, "IP_HARD_BLOCK");
        when(mapper.selectAssessment(11L, "risk-request-1")).thenReturn(null, stored);

        assertThat(service.assess(command).blocked()).isTrue();
    }

    @Test
    void replayWithDifferentDeviceIsRejected() {
        TicketRiskAssessmentRow stored = assessment("ALLOW", 0, "");
        stored.setDeviceHash("f".repeat(64));
        when(mapper.selectAssessment(11L, "risk-request-1")).thenReturn(stored);

        assertThatThrownBy(() -> service.assess(command)).isInstanceOf(BusinessException.class);
    }

    @Test
    void reviewUsesOptimisticVersionAndWritesAudit() {
        TicketRiskReviewRow pending = new TicketRiskReviewRow();
        pending.setAssessmentId(91L);
        pending.setStatus("PENDING");
        pending.setVersion(2L);
        TicketRiskReviewRow approved = new TicketRiskReviewRow();
        approved.setAssessmentId(91L);
        approved.setStatus("APPROVED");
        approved.setVersion(3L);
        when(mapper.lockReview(91L)).thenReturn(pending);
        when(mapper.updateReview(91L, 2L, "APPROVED", 7L,
            "Đã kiểm tra thiết bị hợp lệ", NOW)).thenReturn(1);
        when(mapper.insertReviewAudit(91L, "PENDING", "APPROVED", 7L,
            "Đã kiểm tra thiết bị hợp lệ")).thenReturn(1);
        when(mapper.selectReview(91L)).thenReturn(approved);

        assertThat(service.review(91L, 2L, "APPROVED", 7L,
            "Đã kiểm tra thiết bị hợp lệ", NOW).getStatus()).isEqualTo("APPROVED");
    }

    private TicketRiskRuleRow rule(String code, String metric, long threshold, Integer window,
                                   int score, boolean hardBlock) {
        TicketRiskRuleRow row = new TicketRiskRuleRow();
        row.setRuleCode(code);
        row.setMetricName(metric);
        row.setThresholdValue(threshold);
        row.setWindowMinutes(window);
        row.setScore(score);
        row.setHardBlock(hardBlock);
        return row;
    }

    private TicketRiskAssessmentRow assessment(String action, int score, String rules) {
        TicketRiskAssessmentRow row = new TicketRiskAssessmentRow();
        row.setId(91L);
        row.setUserId(11L);
        row.setSeasonId(21L);
        row.setBookId(31L);
        row.setClientRequestId("risk-request-1");
        row.setDeviceHash(DEVICE_HASH);
        row.setIpHash(IP_HASH);
        row.setRiskScore(score);
        row.setAction(action);
        row.setMatchedRules(rules);
        row.setPolicyVersion("v1");
        return row;
    }
}
