package com.java2nb.novel.core.schedule;

import com.java2nb.novel.service.gamification.AuthorRewardService;
import com.java2nb.novel.service.gamification.config.GamificationConfigProvider;
import com.java2nb.novel.service.gamification.config.GamificationConfigSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Date;

/** Giải phóng từng khoản thưởng đã hết cửa sổ khiếu nại bằng transaction riêng của service. */
@Component
@Slf4j
public class AuthorRewardReleaseSchedule {

    private final GamificationConfigProvider configProvider;
    private final AuthorRewardService authorRewardService;
    private final Clock clock;

    @Autowired
    public AuthorRewardReleaseSchedule(GamificationConfigProvider configProvider,
                                       AuthorRewardService authorRewardService) {
        this(configProvider, authorRewardService, Clock.systemUTC());
    }

    AuthorRewardReleaseSchedule(GamificationConfigProvider configProvider,
                                AuthorRewardService authorRewardService, Clock clock) {
        this.configProvider = configProvider;
        this.authorRewardService = authorRewardService;
        this.clock = clock;
    }

    public void releaseMaturedRewards() {
        GamificationConfigSnapshot config = configProvider.currentForWrite();
        if (!config.isRewardEnabled()) {
            return;
        }
        Date now = Date.from(clock.instant());
        for (Long allocationId : authorRewardService.listMaturedAllocationIds(
            now, config.getJobBatchSize())) {
            try {
                authorRewardService.releaseMaturedReward(allocationId, now);
            } catch (RuntimeException exception) {
                log.error("GAMIFY-ALERT-007 không thể giải phóng thưởng tác giả: allocationId={}",
                    allocationId, exception);
            }
        }
    }
}
