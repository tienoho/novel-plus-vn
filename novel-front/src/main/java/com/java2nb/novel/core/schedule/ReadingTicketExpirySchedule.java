package com.java2nb.novel.core.schedule;

import com.java2nb.novel.core.config.ReaderEntitlementProperties;
import com.java2nb.novel.mapper.ReadingTicketMapper;
import com.java2nb.novel.service.entitlement.ReadingTicketExpiryResult;
import com.java2nb.novel.service.entitlement.ReadingTicketService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Date;
import java.util.List;

@Component
@Slf4j
public class ReadingTicketExpirySchedule {
    private final ReaderEntitlementProperties properties;
    private final ReadingTicketMapper mapper;
    private final ReadingTicketService service;
    private final Clock clock;

    @Autowired
    public ReadingTicketExpirySchedule(ReaderEntitlementProperties properties,
                                       ReadingTicketMapper mapper,
                                       ReadingTicketService service) {
        this(properties, mapper, service, Clock.systemUTC());
    }

    ReadingTicketExpirySchedule(ReaderEntitlementProperties properties,
                                ReadingTicketMapper mapper,
                                ReadingTicketService service, Clock clock) {
        this.properties = properties;
        this.mapper = mapper;
        this.service = service;
        this.clock = clock;
    }

    @Scheduled(cron = "${novel.reader-entitlement.expiry-cron:0 30 3 * * ?}", zone = "UTC")
    public void expireDueLots() {
        if (!properties.isEnabled() || !properties.isConfigured()) {
            return;
        }
        Date cutoff = Date.from(clock.instant());
        try {
            while (true) {
                List<Long> userIds = mapper.selectExpiredLotUserIds(
                    cutoff, properties.getExpiryBatchSize());
                if (userIds.isEmpty()) {
                    return;
                }
                for (Long userId : userIds) {
                    ReadingTicketExpiryResult result = service.expireDueLots(userId, cutoff,
                        properties.getPolicyVersion(), properties.getExpiryMaxLotsPerUser());
                    if (result.ticketCount() > 0) {
                        log.info("Đã đóng {} Vé đọc hết hạn của userId={} trong {} lot",
                            result.ticketCount(), userId, result.lotCount());
                    }
                }
            }
        } catch (RuntimeException exception) {
            log.error("READER-TICKET-ALERT-001 job đóng Vé đọc hết hạn thất bại", exception);
        }
    }
}
