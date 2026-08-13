package com.java2nb.novel.core.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.ZoneId;

/**
 * Cấu hình gamification: Ngọn Đuốc, nhiệm vụ, cảnh giới, kỳ xếp hạng và quỹ thưởng.
 *
 * <p>Mọi cờ {@code enabled} mặc định tắt. Chính sách và giá trị mặc định được giải thích trong
 * {@code doc/gamification-policy-v1.md}; đổi giá trị ở đây mà không cập nhật tài liệu sẽ khiến
 * campaign đã chốt tham chiếu tới một phiên bản chính sách không còn đúng.
 *
 * <p>Lớp này chỉ tồn tại ở {@code novel-front}. Service trong {@code novel-common} nhận ngưỡng qua
 * tham số method, không đọc cấu hình, để module dùng chung không phụ thuộc profile chạy.
 */
@Data
@Slf4j
@Component
@ConfigurationProperties(prefix = "novel.gamification")
public class GamificationProperties {

    private String policyVersion = "v1";

    private String zoneId = "Asia/Ho_Chi_Minh";

    private Event event = new Event();

    private Ticket ticket = new Ticket();

    private Vote vote = new Vote();

    private Quest quest = new Quest();

    private Realm realm = new Realm();

    private Season season = new Season();

    private Reward reward = new Reward();

    private Job job = new Job();

    @Data
    public static class Event {
        private boolean enabled;
        private int drainBatchSize = 200;
        private long drainDelayMs = 15_000;
        private int maxAttempt = 10;
    }

    @Data
    public static class Ticket {
        private boolean enabled;
        private int lotValidityDays = 60;
        private boolean grantOnTopUpEnabled;
        private String expiryCron = "0 20 3 * * ?";
        private int expiryBatchSize = 500;
        private int maxGrantPerBatch = 1000;
    }

    @Data
    public static class Vote {
        private boolean enabled;
        private boolean allowCrawledBooks;
        private String ipHashSalt = "disabled";
        private int maxTicketsPerRequest = 10;
        private int maxVotesPerDay = 20;
        private int maxTicketsPerDay = 50;
        private int maxTicketsPerBookPerSeason = 100;
        private int maxLotsPerSpend = 50;
    }

    @Data
    public static class Quest {
        private boolean enabled;
        private boolean replyQuestEnabled;
        private int readingMinutesTarget = 30;
        private int heartbeatIntervalSeconds = 60;
        private int heartbeatMaxMinutesPerDay = 180;
    }

    @Data
    public static class Realm {
        private boolean enabled;
        private boolean affectsBenefits;
        private int changeCooldownHours = 24;
    }

    @Data
    public static class Season {
        private boolean enabled;
        private String closeCron = "0 5 0 1 * ?";
        private int closeDrainSeconds = 60;
        private long resumeDelayMs = 30_000;
        private boolean autoFinalize;
        private int reviewWindowHours = 72;
    }

    @Data
    public static class Reward {
        private boolean enabled;
        private int claimWindowDays = 7;
        private String releaseCron = "0 40 3 * * ?";
    }

    @Data
    public static class Job {
        private int leaseSeconds = 300;
        private int batchSize = 500;
    }

    public ZoneId resolveZoneId() {
        return ZoneId.of(zoneId);
    }

    public boolean isReadingHeartbeatEnabled() {
        return event.enabled && quest.enabled && isConfigured();
    }

    /**
     * Cấu hình có nhất quán để bật tính năng ghi hay không. Kiểm chéo giữa các ngưỡng, vì một
     * ngưỡng lẻ loi hợp lệ vẫn có thể tạo ra tổ hợp vô nghĩa khi đứng cạnh ngưỡng khác.
     */
    public boolean isConfigured() {
        boolean validZone;
        try {
            resolveZoneId();
            validZone = true;
        } catch (RuntimeException exception) {
            validZone = false;
        }
        return validZone
            && policyVersion != null && !policyVersion.isBlank()
            && event.drainBatchSize > 0
            && event.drainDelayMs > 0
            && event.maxAttempt > 0
            && ticket.lotValidityDays > 0
            && ticket.expiryBatchSize > 0
            && ticket.maxGrantPerBatch > 0
            && vote.maxTicketsPerRequest > 0
            && vote.maxVotesPerDay > 0
            && vote.maxTicketsPerDay >= vote.maxTicketsPerRequest
            && vote.maxTicketsPerBookPerSeason >= vote.maxTicketsPerRequest
            && vote.maxLotsPerSpend > 0
            && (!vote.enabled || isStrongIpHashSalt(vote.ipHashSalt))
            && quest.readingMinutesTarget > 0
            && quest.heartbeatIntervalSeconds > 0
            && quest.heartbeatMaxMinutesPerDay >= quest.readingMinutesTarget
            && realm.changeCooldownHours > 0
            && season.closeDrainSeconds >= 0
            && season.resumeDelayMs > 0
            && season.reviewWindowHours > 0
            && reward.claimWindowDays > 0
            && job.leaseSeconds > 0
            && job.batchSize > 0;
    }

    private boolean isStrongIpHashSalt(String value) {
        return value != null && value.length() >= 32 && !"disabled".equalsIgnoreCase(value);
    }

    /**
     * Cảnh báo lúc khởi động thay vì lúc người dùng bấm nút. Một tổ hợp cấu hình vô lý chỉ lộ ra
     * khi có lưu lượng thật, và lúc đó đã quá muộn để phát hiện bằng log.
     */
    @PostConstruct
    void warnOnInconsistentConfiguration() {
        try {
            resolveZoneId();
        } catch (RuntimeException exception) {
            log.error("GAMIFY-CONFIG: múi giờ gamification không hợp lệ: {}", zoneId, exception);
        }
        boolean anyWriteEnabled = event.enabled || ticket.enabled || vote.enabled || quest.enabled
            || realm.enabled || season.enabled || reward.enabled;
        if (anyWriteEnabled && !isConfigured()) {
            log.error("GAMIFY-CONFIG: đã bật tính năng ghi gamification nhưng cấu hình ngưỡng không nhất quán");
        }
        if (reward.enabled && !season.enabled) {
            log.error("GAMIFY-CONFIG: bật trả thưởng nhưng chưa bật kỳ xếp hạng; sẽ không có kỳ FINALIZED nào để tính thưởng");
        }
        if (vote.enabled && !ticket.enabled) {
            log.error("GAMIFY-CONFIG: bật bỏ phiếu nhưng chưa bật cấp Ngọn Đuốc; người đọc sẽ không có phiếu để thắp");
        }
    }
}
