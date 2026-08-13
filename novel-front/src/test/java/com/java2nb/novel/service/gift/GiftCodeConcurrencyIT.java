package com.java2nb.novel.service.gift;

import com.java2nb.novel.FrontNovelApplication;
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
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Không dùng {@code @Transactional}: mỗi worker phải mở transaction thật để kiểm chứng khóa MySQL.
 * Biên nhận mã quà là bất biến nên test dùng ID động và giữ lại dữ liệu audit trong database integration.
 */
@SpringBootTest(
    classes = FrontNovelApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {"spring.task.scheduling.enabled=false", "management.health.mail.enabled=false"}
)
@EnabledIfSystemProperty(named = "gift.code.concurrency.it", matches = "true")
class GiftCodeConcurrencyIT {
    private static final Date START = Date.from(Instant.parse("2099-01-01T00:00:00Z"));
    private static final Date REDEEMED_AT = Date.from(Instant.parse("2099-06-01T00:00:00Z"));
    private static final Date END = Date.from(Instant.parse("2100-01-01T00:00:00Z"));

    @Autowired private GiftCodeService service;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void twoUsersCompetingForSingleUseCodeCreateExactlyOneReward() throws Exception {
        long suffix = nextSuffix();
        long firstUser = 99_811_000_000L + suffix;
        long secondUser = firstUser + 1;
        insertUser(firstUser, "gift_single_a_" + suffix);
        insertUser(secondUser, "gift_single_b_" + suffix);

        GiftCampaignRow campaign = createActiveCampaign("SINGLE_" + suffix, 2);
        String code = service.issueCodes(campaign.getId(), 1, 1).get(0);
        long codeId = codeIds(campaign.getId()).get(0);

        List<String> outcomes = redeemConcurrently(List.of(
            command(firstUser, code, "single_a_" + suffix),
            command(secondUser, code, "single_b_" + suffix)));

        assertOnePosted(outcomes);
        assertCampaignRewardCounts(campaign.getId(), List.of(codeId), 1);
        assertThat(readerBalance(firstUser, secondUser)).isEqualTo(10L);
    }

    @Test
    void twoCodesCompetingForLastCampaignSlotCreateExactlyOneReward() throws Exception {
        long suffix = nextSuffix();
        long firstUser = 99_812_000_000L + suffix;
        long secondUser = firstUser + 1;
        insertUser(firstUser, "gift_campaign_a_" + suffix);
        insertUser(secondUser, "gift_campaign_b_" + suffix);

        GiftCampaignRow campaign = createActiveCampaign("CAMPAIGN_" + suffix, 1);
        List<String> codes = service.issueCodes(campaign.getId(), 2, 1);
        List<Long> codeIds = codeIds(campaign.getId());

        List<String> outcomes = redeemConcurrently(List.of(
            command(firstUser, codes.get(0), "campaign_a_" + suffix),
            command(secondUser, codes.get(1), "campaign_b_" + suffix)));

        assertOnePosted(outcomes);
        assertCampaignRewardCounts(campaign.getId(), codeIds, 1);
        assertThat(readerBalance(firstUser, secondUser)).isEqualTo(10L);
    }

    @Test
    void sameUserRedeemingTwoCodesConcurrentlyCannotExceedCampaignLimit() throws Exception {
        long suffix = nextSuffix();
        long userId = 99_817_000_000L + suffix;
        insertUser(userId, "gift_user_limit_" + suffix);

        GiftCampaignRow campaign = createActiveCampaign("USER_LIMIT_" + suffix, 2, 1);
        List<String> codes = service.issueCodes(campaign.getId(), 2, 1);
        List<Long> codeIds = codeIds(campaign.getId());

        List<String> outcomes = redeemConcurrently(List.of(
            command(userId, codes.get(0), "user_limit_a_" + suffix),
            command(userId, codes.get(1), "user_limit_b_" + suffix)));

        assertThat(outcomes).containsExactlyInAnyOrder("POSTED", "GiftCodeRedeemException");
        assertCampaignRewardCounts(campaign.getId(), codeIds, 1);
        assertThat(readerBalance(userId)).isEqualTo(10L);
    }

    @Test
    void concurrentRetryForSameUserAndCodeCreatesOneRedemptionAndLedger() throws Exception {
        long suffix = nextSuffix();
        long userId = 99_813_000_000L + suffix;
        insertUser(userId, "gift_retry_" + suffix);

        GiftCampaignRow campaign = createActiveCampaign("RETRY_" + suffix, 1);
        String code = service.issueCodes(campaign.getId(), 1, 1).get(0);
        long codeId = codeIds(campaign.getId()).get(0);
        GiftRedeemCommand retry = command(userId, code, "same_retry_" + suffix);

        List<String> outcomes = redeemConcurrently(List.of(retry, retry));

        assertThat(outcomes).containsExactlyInAnyOrder("POSTED", "ALREADY_REDEEMED");
        assertCampaignRewardCounts(campaign.getId(), List.of(codeId), 1);
        assertThat(readerBalance(userId)).isEqualTo(10L);
    }

    @Test
    void reusingClientRequestIdForDifferentCodeIsRejectedWithoutSecondReward() {
        long suffix = nextSuffix();
        long userId = 99_814_000_000L + suffix;
        insertUser(userId, "gift_request_" + suffix);

        GiftCampaignRow campaign = createActiveCampaign("REQUEST_" + suffix, 2, 2);
        List<String> codes = service.issueCodes(campaign.getId(), 2, 1);
        List<Long> codeIds = codeIds(campaign.getId());
        String clientRequestId = "shared_request_" + suffix;

        service.redeem(command(userId, codes.get(0), clientRequestId));

        assertThatThrownBy(() -> service.redeem(
            command(userId, codes.get(1), clientRequestId)))
            .isInstanceOf(GiftCodeRedeemException.class);
        assertCampaignRewardCounts(campaign.getId(), codeIds, 1);
        assertThat(readerBalance(userId)).isEqualTo(10L);
    }

    @Test
    void revokeAndRedeemRaceHasExactlyOneTerminalOutcome() throws Exception {
        long suffix = nextSuffix();
        long userId = 99_816_000_000L + suffix;
        insertUser(userId, "gift_revoke_race_" + suffix);
        GiftCampaignRow campaign = createActiveCampaign("REVOKE_" + suffix, 1);
        String code = service.issueCodes(campaign.getId(), 1, 1).get(0);
        GiftCodeRow codeRow = service.listCodes(campaign.getId(), 1, 20).items().get(0);

        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> redeem = executor.submit(() -> {
                startGate.await();
                try {
                    return "REDEEM:" + service.redeem(command(
                        userId, code, "revoke_race_" + suffix)).status().name();
                } catch (RuntimeException exception) {
                    return "REDEEM:" + exception.getClass().getSimpleName();
                }
            });
            Future<String> revoke = executor.submit(() -> {
                startGate.await();
                try {
                    return "REVOKE:" + service.revokeCode(
                        codeRow.getId(), codeRow.getVersion()).getStatus();
                } catch (RuntimeException exception) {
                    return "REVOKE:" + exception.getClass().getSimpleName();
                }
            });
            startGate.countDown();
            List<String> outcomes = List.of(
                redeem.get(30, TimeUnit.SECONDS), revoke.get(30, TimeUnit.SECONDS));
            boolean redeemed = outcomes.contains("REDEEM:POSTED");
            boolean revoked = outcomes.contains("REVOKE:REVOKED");

            assertThat(redeemed).isNotEqualTo(revoked);
            assertCampaignRewardCounts(campaign.getId(), List.of(codeRow.getId()),
                redeemed ? 1 : 0);
            assertThat(readerBalance(userId)).isEqualTo(redeemed ? 10L : 0L);
            assertThat(service.listCodes(campaign.getId(), 1, 20).items().get(0).getStatus())
                .isEqualTo(redeemed ? "EXHAUSTED" : "REVOKED");
        } finally {
            executor.shutdownNow();
        }
    }

    private GiftCampaignRow createActiveCampaign(String campaignCode, long maxRedemptions) {
        return createActiveCampaign(campaignCode, maxRedemptions, 1);
    }

    private GiftCampaignRow createActiveCampaign(String campaignCode, long maxRedemptions,
                                                  int maxPerUser) {
        GiftCampaignRow campaign = service.createCampaign(new GiftCampaignCommand(
            campaignCode, "Chiến dịch concurrency " + campaignCode, "XU", 10, null,
            START, END, maxRedemptions, maxPerUser));
        return service.changeCampaignStatus(campaign.getId(), campaign.getVersion(), "ACTIVE");
    }

    private List<String> redeemConcurrently(List<GiftRedeemCommand> commands) throws Exception {
        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(commands.size());
        List<Future<String>> futures = new ArrayList<>();
        try {
            for (GiftRedeemCommand command : commands) {
                futures.add(executor.submit(() -> {
                    startGate.await();
                    try {
                        return service.redeem(command).status().name();
                    } catch (RuntimeException exception) {
                        return exception.getClass().getSimpleName();
                    }
                }));
            }
            startGate.countDown();
            List<String> outcomes = new ArrayList<>(futures.size());
            for (Future<String> future : futures) {
                outcomes.add(future.get(30, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            executor.shutdownNow();
        }
    }

    private void assertOnePosted(List<String> outcomes) {
        assertThat(outcomes).hasSize(2);
        assertThat(outcomes.stream().filter("POSTED"::equals).count()).isEqualTo(1);
        assertThat(outcomes.stream().filter("POSTED"::equals).count())
            .isLessThan(outcomes.size());
    }

    private void assertCampaignRewardCounts(long campaignId, List<Long> codeIds,
                                            int expectedCount) {
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM gift_redemption WHERE campaign_id=?",
            Integer.class, campaignId)).isEqualTo(expectedCount);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT redeemed_count FROM gift_campaign WHERE id=?",
            Long.class, campaignId)).isEqualTo((long) expectedCount);
        String placeholders = String.join(",", java.util.Collections.nCopies(codeIds.size(), "?"));
        List<Object> parameters = new ArrayList<>();
        parameters.add("REWARD");
        parameters.addAll(codeIds.stream().map(String::valueOf).toList());
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ledger_transaction WHERE business_type=? AND business_id IN ("
                + placeholders + ')',
            Integer.class, parameters.toArray())).isEqualTo(expectedCount);
    }

    private long readerBalance(long... userIds) {
        String placeholders = String.join(",", java.util.Collections.nCopies(userIds.length, "?"));
        Object[] parameters = java.util.Arrays.stream(userIds).boxed().toArray();
        Long balance = jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(account_balance), 0) FROM user WHERE id IN (" + placeholders + ')',
            Long.class, parameters);
        return balance == null ? 0L : balance;
    }

    private List<Long> codeIds(long campaignId) {
        return jdbcTemplate.queryForList(
            "SELECT id FROM gift_code WHERE campaign_id=? ORDER BY id", Long.class, campaignId);
    }

    private GiftRedeemCommand command(long userId, String code, String clientRequestId) {
        return new GiftRedeemCommand(userId, code, clientRequestId, REDEEMED_AT);
    }

    private void insertUser(long userId, String username) {
        jdbcTemplate.update("""
            INSERT INTO user
                (id, username, password, nick_name, account_balance, status, create_time, update_time)
            VALUES (?, ?, 'gift-code-concurrency-test', ?, 0, 0, NOW(), NOW())
            """, userId, username, username);
    }

    private long nextSuffix() {
        return Math.floorMod(System.nanoTime(), 800_000L);
    }
}
