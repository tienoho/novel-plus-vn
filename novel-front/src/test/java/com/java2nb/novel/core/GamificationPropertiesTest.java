package com.java2nb.novel.core;

import com.java2nb.novel.core.config.GamificationProperties;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bảo vệ khung cấu hình gamification.
 *
 * <p>Hai rủi ro được nhắm tới. Thứ nhất, một cờ bị bật nhầm mặc định sẽ mở đường ghi vào sổ cái
 * trên môi trường chưa sẵn sàng. Thứ hai, một biến môi trường có trong `application.yml` nhưng
 * thiếu trong `.env.example` sẽ khiến người deploy chạy với giá trị mặc định ngầm mà không biết.
 */
class GamificationPropertiesTest {

    private static final Pattern ENV_PLACEHOLDER = Pattern.compile("\\$\\{(GAMIFICATION_[A-Z0-9_]+):");

    @Test
    void everyFeatureFlagDefaultsToDisabled() {
        GamificationProperties properties = new GamificationProperties();

        assertThat(properties.getEvent().isEnabled()).isFalse();
        assertThat(properties.getTicket().isEnabled()).isFalse();
        assertThat(properties.getVote().isEnabled()).isFalse();
        assertThat(properties.getQuest().isEnabled()).isFalse();
        assertThat(properties.getRealm().isEnabled()).isFalse();
        assertThat(properties.getSeason().isEnabled()).isFalse();
        assertThat(properties.getReward().isEnabled()).isFalse();
    }

    @Test
    void conservativePolicyDefaultsMatchVersionOne() {
        GamificationProperties properties = new GamificationProperties();

        assertThat(properties.getPolicyVersion()).isEqualTo("v1");
        assertThat(properties.resolveZoneId()).isEqualTo(ZoneId.of("Asia/Ho_Chi_Minh"));
        // Không bán Đuốc và không cấp theo nạp Xu ở v1; đây là điều khiến chargeback không tạo nợ Đuốc.
        assertThat(properties.getTicket().isGrantOnTopUpEnabled()).isFalse();
        // Truyện crawl không đủ điều kiện nhận phiếu, nên cũng không nhận thưởng.
        assertThat(properties.getVote().isAllowCrawledBooks()).isFalse();
        // Cảnh giới thuần danh xưng, nằm ngoài đường tài chính.
        assertThat(properties.getRealm().isAffectsBenefits()).isFalse();
        // Mọi kỳ phải qua mắt người trước khi chốt.
        assertThat(properties.getSeason().isAutoFinalize()).isFalse();
        // Nhiệm vụ trả lời bình luận tắt; nhiệm vụ bình luận là thành tựu một lần.
        assertThat(properties.getQuest().isReplyQuestEnabled()).isFalse();
        assertThat(properties.getReward().getClaimWindowDays()).isEqualTo(7);
    }

    @Test
    void defaultConfigurationIsInternallyConsistent() {
        assertThat(new GamificationProperties().isConfigured()).isTrue();
    }

    @Test
    void readingHeartbeatRequiresEventQuestAndValidConfiguration() {
        GamificationProperties properties = new GamificationProperties();
        assertThat(properties.isReadingHeartbeatEnabled()).isFalse();

        properties.getEvent().setEnabled(true);
        assertThat(properties.isReadingHeartbeatEnabled()).isFalse();

        properties.getQuest().setEnabled(true);
        assertThat(properties.isReadingHeartbeatEnabled()).isTrue();

        properties.getQuest().setHeartbeatIntervalSeconds(0);
        assertThat(properties.isReadingHeartbeatEnabled()).isFalse();
    }

    @Test
    void rejectsThresholdCombinationsThatContradictEachOther() {
        GamificationProperties dailyBelowSingleRequest = new GamificationProperties();
        dailyBelowSingleRequest.getVote().setMaxTicketsPerRequest(10);
        dailyBelowSingleRequest.getVote().setMaxTicketsPerDay(5);
        assertThat(dailyBelowSingleRequest.isConfigured())
            .describedAs("trần ngày thấp hơn trần một lần gửi khiến không lần thắp nào hợp lệ")
            .isFalse();

        GamificationProperties bookQuotaBelowSingleRequest = new GamificationProperties();
        bookQuotaBelowSingleRequest.getVote().setMaxTicketsPerRequest(10);
        bookQuotaBelowSingleRequest.getVote().setMaxTicketsPerBookPerSeason(3);
        assertThat(bookQuotaBelowSingleRequest.isConfigured()).isFalse();

        GamificationProperties readTargetAboveDailyCap = new GamificationProperties();
        readTargetAboveDailyCap.getQuest().setReadingMinutesTarget(200);
        readTargetAboveDailyCap.getQuest().setHeartbeatMaxMinutesPerDay(180);
        assertThat(readTargetAboveDailyCap.isConfigured())
            .describedAs("mục tiêu đọc vượt trần ngày khiến nhiệm vụ không thể hoàn thành")
            .isFalse();

        GamificationProperties zeroLease = new GamificationProperties();
        zeroLease.getJob().setLeaseSeconds(0);
        assertThat(zeroLease.isConfigured()).isFalse();

        GamificationProperties invalidZone = new GamificationProperties();
        invalidZone.setZoneId("Asia/Khong-Ton-Tai");
        assertThat(invalidZone.isConfigured())
            .describedAs("múi giờ sai không được coi là cấu hình có thể ghi dữ liệu nghiệp vụ")
            .isFalse();

        GamificationProperties zeroEventBatch = new GamificationProperties();
        zeroEventBatch.getEvent().setDrainBatchSize(0);
        assertThat(zeroEventBatch.isConfigured()).isFalse();

        GamificationProperties zeroGrantLimit = new GamificationProperties();
        zeroGrantLimit.getTicket().setMaxGrantPerBatch(0);
        assertThat(zeroGrantLimit.isConfigured()).isFalse();

        GamificationProperties zeroReviewWindow = new GamificationProperties();
        zeroReviewWindow.getSeason().setReviewWindowHours(0);
        assertThat(zeroReviewWindow.isConfigured()).isFalse();

        GamificationProperties voteWithoutPrivateSalt = new GamificationProperties();
        voteWithoutPrivateSalt.getVote().setEnabled(true);
        assertThat(voteWithoutPrivateSalt.isConfigured())
            .describedAs("bật vote với muối IP mặc định sẽ khiến hash dễ dò ngược")
            .isFalse();
        voteWithoutPrivateSalt.getVote().setIpHashSalt("0123456789abcdef0123456789abcdef");
        assertThat(voteWithoutPrivateSalt.isConfigured()).isTrue();
    }

    @Test
    void everyGamificationEnvironmentVariableIsDocumented() throws Exception {
        Path repository = Path.of("").toAbsolutePath().normalize().getParent();
        String applicationYml = Files.readString(
            repository.resolve("novel-front/src/main/resources/application.yml"), StandardCharsets.UTF_8);
        String envExample = Files.readString(repository.resolve(".env.example"), StandardCharsets.UTF_8);

        Set<String> declaredInYml = new TreeSet<>();
        Matcher matcher = ENV_PLACEHOLDER.matcher(applicationYml);
        while (matcher.find()) {
            declaredInYml.add(matcher.group(1));
        }

        assertThat(declaredInYml)
            .describedAs("application.yml phải khai báo khối novel.gamification")
            .isNotEmpty();

        Set<String> documented = new TreeSet<>();
        for (String line : envExample.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("GAMIFICATION_") && trimmed.contains("=")) {
                documented.add(trimmed.substring(0, trimmed.indexOf('=')));
            }
        }

        assertThat(documented)
            .describedAs("mọi biến GAMIFICATION_* dùng trong application.yml phải có trong .env.example")
            .containsAll(declaredInYml);
        assertThat(declaredInYml)
            .describedAs(".env.example không được chứa biến GAMIFICATION_* mà application.yml không đọc")
            .containsAll(documented);
    }

    @Test
    void policyDocumentRecordsTheVersionTheCodeDefaultsTo() throws Exception {
        Path repository = Path.of("").toAbsolutePath().normalize().getParent();
        Path policy = repository.resolve("doc/gamification-policy-v1.md");

        assertThat(policy).exists();
        String content = Files.readString(policy, StandardCharsets.UTF_8);
        assertThat(content).contains("`" + new GamificationProperties().getPolicyVersion() + "`");
        // Sáu quyết định phải có mặt; thiếu một cái nghĩa là code đang chạy trước chính sách.
        assertThat(content).contains("## QĐ-1", "## QĐ-2", "## QĐ-3", "## QĐ-4", "## QĐ-5", "## QĐ-6");
    }
}
