package com.java2nb.novel.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.ZoneId;

/** Phần cấu hình tối thiểu mà trang quản trị cần để cấp Ngọn Đuốc. */
@Data
@Component
@ConfigurationProperties(prefix = "novel.gamification")
public class GamificationAdminSettings {

    private String policyVersion = "v1";
    private String zoneId = "Asia/Ho_Chi_Minh";
    private Event event = new Event();
    private Ticket ticket = new Ticket();
    private Season season = new Season();
    private Job job = new Job();
    private Reward reward = new Reward();

    @Data
    public static class Event {
        private boolean enabled;
    }

    @Data
    public static class Ticket {
        private boolean enabled;
        private int lotValidityDays = 60;
        private int maxGrantPerBatch = 1000;
    }

    @Data
    public static class Season {
        private boolean enabled;
        private int closeDrainSeconds = 60;
    }

    @Data
    public static class Job {
        private int leaseSeconds = 300;
        private int batchSize = 500;
    }

    @Data
    public static class Reward {
        private boolean enabled;
        private int claimWindowDays = 7;
    }

    public boolean isConfigured() {
        try {
            ZoneId.of(zoneId);
        } catch (RuntimeException exception) {
            return false;
        }
        return policyVersion != null && !policyVersion.trim().isEmpty()
            && ticket.lotValidityDays > 0 && ticket.maxGrantPerBatch > 0
            && season.closeDrainSeconds >= 0 && job.leaseSeconds > 0 && job.batchSize > 0
            && reward.claimWindowDays > 0;
    }

    public ZoneId resolveZoneId() {
        return ZoneId.of(zoneId);
    }
}
