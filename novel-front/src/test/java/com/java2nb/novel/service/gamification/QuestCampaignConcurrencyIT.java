package com.java2nb.novel.service.gamification;

import com.java2nb.novel.FrontNovelApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    classes = FrontNovelApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {"spring.task.scheduling.enabled=false", "management.health.mail.enabled=false"}
)
@EnabledIfSystemProperty(named = "gamification.concurrency.it", matches = "true")
class QuestCampaignConcurrencyIT {
    @Autowired
    private QuestCampaignConfigService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<String> campaignCodes = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (String code : campaignCodes) {
            jdbcTemplate.update("DELETE FROM quest_reward WHERE campaign_code=?", code);
            jdbcTemplate.update("DELETE FROM quest_campaign WHERE campaign_code=?", code);
        }
    }

    @Test
    void concurrentOverlappingActivationsLeaveExactlyOneActiveCampaign() throws Exception {
        long suffix = Math.floorMod(System.nanoTime(), 1_000_000);
        String firstCode = "CONCURRENT_A_" + suffix;
        String secondCode = "CONCURRENT_B_" + suffix;
        campaignCodes.add(firstCode);
        campaignCodes.add(secondCode);
        Date startAt = Date.from(Instant.parse("2096-01-01T00:00:00Z"));
        Date endAt = Date.from(Instant.parse("2096-02-01T00:00:00Z"));
        Date activatedAt = Date.from(Instant.parse("2095-12-01T00:00:00Z"));
        QuestCampaignRow first = createReadyDraft(firstCode, startAt, endAt);
        QuestCampaignRow second = createReadyDraft(secondCode, startAt, endAt);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> firstResult = executor.submit(
                () -> activateWhenReleased(first.getId(), activatedAt, ready, start));
            Future<Boolean> secondResult = executor.submit(
                () -> activateWhenReleased(second.getId(), activatedAt, ready, start));
            ready.await();
            start.countDown();

            assertThat(List.of(firstResult.get(), secondResult.get()))
                .containsExactlyInAnyOrder(true, false);
            assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM quest_campaign
                WHERE campaign_code IN (?, ?) AND status='ACTIVE'
                """, Integer.class, firstCode, secondCode)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private QuestCampaignRow createReadyDraft(String code, Date startAt, Date endAt) {
        QuestCampaignRow campaign = service.createDraft(
            new QuestCampaignDraftCommand(code, startAt, endAt, "v1"));
        service.replaceReward(campaign.getId(),
            new QuestRewardCommand("DAILY_READING", 10L, 1L));
        return campaign;
    }

    private boolean activateWhenReleased(long campaignId, Date activatedAt,
                                         CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            start.await();
            service.activate(campaignId, activatedAt);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }
}
