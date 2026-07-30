package com.java2nb.novel.core.schedule;

import com.java2nb.novel.core.config.GamificationProperties;
import com.java2nb.novel.service.gamification.AuthorRewardService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Date;

/** Giải phóng từng khoản thưởng đã hết cửa sổ khiếu nại bằng transaction riêng của service. */
@Component
@Slf4j
public class AuthorRewardReleaseSchedule {

    private final GamificationProperties properties;
    private final AuthorRewardService authorRewardService;
    private final Clock clock;

    @Autowired
    public AuthorRewardReleaseSchedule(GamificationProperties properties,
                                       AuthorRewardService authorRewardService) {
        this(properties, authorRewardService, Clock.systemUTC());
    }

    AuthorRewardReleaseSchedule(GamificationProperties properties,
                                AuthorRewardService authorRewardService, Clock clock) {
        this.properties = properties;
        this.authorRewardService = authorRewardService;
        this.clock = clock;
    }

    @Scheduled(
        cron = "${novel.gamification.reward.release-cron:0 40 3 * * ?}",
        zone = "${novel.gamification.zone-id:Asia/Ho_Chi_Minh}"
    )
    public void releaseMaturedRewards() {
        if (!properties.getReward().isEnabled() || !properties.isConfigured()) {
            return;
        }
        Date now = Date.from(clock.instant());
        Date cutoff = Date.from(clock.instant().minus(
            Duration.ofDays(properties.getReward().getClaimWindowDays())));
        for (Long allocationId : authorRewardService.listMaturedAllocationIds(
            cutoff, properties.getJob().getBatchSize())) {
            try {
                authorRewardService.releaseMaturedReward(allocationId, now);
            } catch (RuntimeException exception) {
                log.error("GAMIFY-ALERT-007 không thể giải phóng thưởng tác giả: allocationId={}",
                    allocationId, exception);
            }
        }
    }
}
