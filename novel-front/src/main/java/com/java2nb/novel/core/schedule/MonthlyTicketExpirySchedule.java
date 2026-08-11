package com.java2nb.novel.core.schedule;

import com.java2nb.novel.mapper.MonthlyTicketMapper;
import com.java2nb.novel.service.gamification.MonthlyTicketService;
import com.java2nb.novel.service.gamification.TicketExpiryResult;
import com.java2nb.novel.service.gamification.config.GamificationConfigProvider;
import com.java2nb.novel.service.gamification.config.GamificationConfigSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/** Đóng lot Ngọn Đuốc hết hạn theo từng người dùng và lưu checkpoint để chạy lại an toàn. */
@Component
@Slf4j
public class MonthlyTicketExpirySchedule {

    private static final String JOB_TYPE = "LOT_EXPIRY";
    private static final String SCOPE_TYPE = "DATE";

    private final GamificationConfigProvider configProvider;
    private final MonthlyTicketMapper monthlyTicketMapper;
    private final MonthlyTicketService monthlyTicketService;
    private final Clock clock;
    private final String ownerInstance;

    @Autowired
    public MonthlyTicketExpirySchedule(GamificationConfigProvider configProvider,
                                       MonthlyTicketMapper monthlyTicketMapper,
                                       MonthlyTicketService monthlyTicketService) {
        this(configProvider, monthlyTicketMapper, monthlyTicketService, Clock.systemUTC(),
            "lot-expiry-" + UUID.randomUUID());
    }

    MonthlyTicketExpirySchedule(GamificationConfigProvider configProvider,
                                MonthlyTicketMapper monthlyTicketMapper,
                                MonthlyTicketService monthlyTicketService,
                                Clock clock, String ownerInstance) {
        this.configProvider = configProvider;
        this.monthlyTicketMapper = monthlyTicketMapper;
        this.monthlyTicketService = monthlyTicketService;
        this.clock = clock;
        this.ownerInstance = ownerInstance;
    }

    public void expireDueLots() {
        GamificationConfigSnapshot config = configProvider.currentForWrite();
        if (!config.isTicketEnabled()) {
            return;
        }

        Instant runInstant = clock.instant();
        Date cutoff = Date.from(runInstant);
        LocalDate localDate = runInstant.atZone(java.time.ZoneId.of(config.getZoneId())).toLocalDate();
        String scopeKey = localDate.toString();
        Date staleBefore = Date.from(runInstant.minusSeconds(config.getJobLeaseSeconds()));
        boolean claimed = monthlyTicketMapper.insertJobRunIgnore(
            JOB_TYPE, SCOPE_TYPE, scopeKey, ownerInstance, cutoff) == 1;
        if (!claimed) {
            claimed = monthlyTicketMapper.claimStaleJobRun(JOB_TYPE, SCOPE_TYPE, scopeKey,
                ownerInstance, cutoff, staleBefore) == 1;
        }
        if (!claimed) {
            return;
        }

        try {
            long afterUserId = parseCheckpoint(monthlyTicketMapper.selectJobCheckpoint(
                JOB_TYPE, SCOPE_TYPE, scopeKey, ownerInstance));
            int batchSize = config.getTicketExpiryBatchSize();
            while (true) {
                List<Long> userIds = monthlyTicketMapper.selectExpiredLotUserIds(
                    cutoff, afterUserId, batchSize);
                if (userIds.isEmpty()) {
                    break;
                }
                for (Long userId : userIds) {
                    TicketExpiryResult result = monthlyTicketService.expireDueLots(
                        userId, cutoff, scopeKey, config.getPolicyVersion());
                    Date heartbeatAt = Date.from(clock.instant());
                    if (monthlyTicketMapper.advanceJobCheckpoint(JOB_TYPE, SCOPE_TYPE, scopeKey,
                        ownerInstance, Long.toString(userId), result.lotCount(), heartbeatAt) != 1) {
                        throw new IllegalStateException("Mất quyền sở hữu job đóng Đuốc hết hạn");
                    }
                    afterUserId = userId;
                }
                if (userIds.size() < batchSize) {
                    break;
                }
            }

            Date finishedAt = Date.from(clock.instant());
            if (monthlyTicketMapper.completeJobRun(JOB_TYPE, SCOPE_TYPE, scopeKey,
                ownerInstance, finishedAt) != 1) {
                throw new IllegalStateException("Không thể hoàn tất job đóng Đuốc hết hạn");
            }
        } catch (RuntimeException exception) {
            Date failedAt = Date.from(clock.instant());
            monthlyTicketMapper.failJobRun(JOB_TYPE, SCOPE_TYPE, scopeKey, ownerInstance,
                failedAt, errorMessage(exception));
            log.error("GAMIFY-ALERT-005 job đóng Ngọn Đuốc hết hạn thất bại: scope={}",
                scopeKey, exception);
        }
    }

    private long parseCheckpoint(String checkpoint) {
        return checkpoint == null || checkpoint.isBlank() ? 0 : Long.parseLong(checkpoint);
    }

    private String errorMessage(RuntimeException exception) {
        String message = exception.getClass().getSimpleName() + ": "
            + (exception.getMessage() == null ? "không có thông tin" : exception.getMessage());
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
