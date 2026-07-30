package com.java2nb.novel.core.schedule;

import com.java2nb.novel.core.config.ReaderEntitlementProperties;
import com.java2nb.novel.mapper.ReadingSubscriptionMapper;
import com.java2nb.novel.service.subscription.ReadingSubscriptionGrantResult;
import com.java2nb.novel.service.subscription.ReadingSubscriptionGrantStatus;
import com.java2nb.novel.service.subscription.ReadingSubscriptionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

@Component
@Slf4j
public class ReadingSubscriptionGrantSchedule {
    private final ReaderEntitlementProperties properties;
    private final ReadingSubscriptionMapper mapper;
    private final ReadingSubscriptionService service;
    private final Clock clock;

    @Autowired
    public ReadingSubscriptionGrantSchedule(ReaderEntitlementProperties properties,
                                            ReadingSubscriptionMapper mapper,
                                            ReadingSubscriptionService service) {
        this(properties, mapper, service, Clock.systemUTC());
    }

    ReadingSubscriptionGrantSchedule(ReaderEntitlementProperties properties,
                                     ReadingSubscriptionMapper mapper,
                                     ReadingSubscriptionService service, Clock clock) {
        this.properties = properties;
        this.mapper = mapper;
        this.service = service;
        this.clock = clock;
    }

    @Scheduled(
        cron = "${novel.reader-entitlement.subscription-grant-cron:0 10 0 * * ?}",
        zone = "${novel.reader-entitlement.subscription-zone-id:Asia/Ho_Chi_Minh}")
    public void grantDuePeriods() {
        if (!properties.isEnabled() || !properties.isConfigured()) {
            return;
        }
        Date now = Date.from(clock.instant());
        ZoneId zoneId = ZoneId.of(properties.getSubscriptionZoneId());
        List<Long> subscriptionIds;
        try {
            subscriptionIds = mapper.selectDueSubscriptionIds(
                now, properties.getSubscriptionGrantBatchSize());
        } catch (RuntimeException exception) {
            log.error("READER-SUBSCRIPTION-ALERT-001 không thể tải thuê bao đến hạn", exception);
            return;
        }
        for (Long subscriptionId : subscriptionIds) {
            try {
                ReadingSubscriptionGrantResult result = service.grantDuePeriod(
                    subscriptionId, now, zoneId, properties.getPolicyVersion());
                if (result.status() == ReadingSubscriptionGrantStatus.POSTED) {
                    log.info("Đã cấp {} Vé đọc cho subscriptionId={} trong kỳ bắt đầu {}",
                        result.ticketAmount(), subscriptionId, result.periodStart());
                }
            } catch (RuntimeException exception) {
                log.error("READER-SUBSCRIPTION-ALERT-002 cấp Vé đọc theo kỳ thất bại: "
                    + "subscriptionId={}", subscriptionId, exception);
            }
        }
    }
}
