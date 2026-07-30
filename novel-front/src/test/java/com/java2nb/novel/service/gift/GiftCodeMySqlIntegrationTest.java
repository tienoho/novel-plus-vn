package com.java2nb.novel.service.gift;

import com.java2nb.novel.FrontNovelApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    classes = FrontNovelApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {"spring.task.scheduling.enabled=false", "management.health.mail.enabled=false"}
)
@EnabledIfSystemProperty(named = "gift.code.mysql.it", matches = "true")
@Transactional
@Rollback
class GiftCodeMySqlIntegrationTest {
    @Autowired private GiftCodeService service;
    @Autowired private GiftCodeProperties properties;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void xuAndReadingTicketRewardsUseTheirAuthoritativeLedgers() {
        long suffix = Math.floorMod(System.nanoTime(), 800_000L);
        Date now = Date.from(Instant.parse("2027-01-15T00:00:00Z"));
        Date start = Date.from(Instant.parse("2027-01-01T00:00:00Z"));
        Date end = Date.from(Instant.parse("2027-02-01T00:00:00Z"));

        long xuUser = 99_810_000_000L + suffix;
        long ticketUser = xuUser + 1;
        insertUser(xuUser, "gift_xu_" + suffix);
        insertUser(ticketUser, "gift_ticket_" + suffix);
        GiftCampaignRow xuCampaign = activate(service.createCampaign(new GiftCampaignCommand(
            "XU_" + suffix, "Chiến dịch Xu MySQL", "XU", 25, null,
            start, end, 10, 1)));
        String xuCode = service.issueCodes(xuCampaign.getId(), 1, 1).get(0);
        GiftRedemptionResult xuPosted = service.redeem(
            new GiftRedeemCommand(xuUser, xuCode, "xu_request_" + suffix, now));
        GiftRedemptionResult xuReplay = service.redeem(
            new GiftRedeemCommand(xuUser, xuCode, "xu_retry__" + suffix, now));

        assertThat(xuPosted.status()).isEqualTo(GiftRedemptionStatus.POSTED);
        assertThat(xuReplay.status()).isEqualTo(GiftRedemptionStatus.ALREADY_REDEEMED);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT available_balance FROM wallet_account
            WHERE owner_type='USER' AND owner_id=? AND account_type='READER_XU'
            """, Long.class, xuUser)).isEqualTo(25L);

        GiftCampaignRow ticketCampaign = activate(service.createCampaign(new GiftCampaignCommand(
            "TICKET_" + suffix, "Chiến dịch Vé MySQL", "READING_TICKET", 3, 45,
            start, end, 10, 1)));
        String ticketCode = service.issueCodes(ticketCampaign.getId(), 1, 1).get(0);
        GiftRedemptionResult ticketPosted = service.redeem(new GiftRedeemCommand(
            ticketUser, ticketCode, "ticket_req_" + suffix, now));

        assertThat(ticketPosted.status()).isEqualTo(GiftRedemptionStatus.POSTED);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT available_balance FROM reading_ticket_account WHERE user_id=?
            """, Long.class, ticketUser)).isEqualTo(3L);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM gift_redemption WHERE user_id IN (?, ?)
            """, Integer.class, xuUser, ticketUser)).isEqualTo(2);
    }

    @Test
    void historyAndUnusedCodeRevocationUseDatabaseState() {
        long suffix = Math.floorMod(System.nanoTime(), 800_000L);
        long userId = 99_815_000_000L + suffix;
        insertUser(userId, "gift_history_" + suffix);
        Date now = Date.from(Instant.parse("2027-01-15T00:00:00Z"));
        GiftCampaignRow campaign = activate(service.createCampaign(new GiftCampaignCommand(
            "HISTORY_" + suffix, "Chiến dịch lịch sử MySQL", "XU", 12, null,
            Date.from(Instant.parse("2027-01-01T00:00:00Z")),
            Date.from(Instant.parse("2027-02-01T00:00:00Z")), 2, 1)));
        java.util.List<String> codes = service.issueCodes(campaign.getId(), 2, 1);
        GiftCodePage codePage = service.listCodes(campaign.getId(), 1, 20);
        GiftCodeRow revoked = service.revokeCode(
            codePage.items().get(0).getId(), codePage.items().get(0).getVersion());
        GiftCodeRow active = service.listCodes(campaign.getId(), 1, 20).items().stream()
            .filter(code -> "ACTIVE".equals(code.getStatus())).findFirst().orElseThrow();

        service.redeem(new GiftRedeemCommand(
            userId, codes.get(0), "history_request_" + suffix, now));

        GiftRedemptionHistoryPage userHistory = service.listUserRedemptions(userId, 1, 20);
        GiftRedemptionHistoryPage adminHistory = service.listCampaignRedemptions(
            campaign.getId(), 1, 20);
        assertThat(revoked.getStatus()).isEqualTo("REVOKED");
        assertThat(userHistory.total()).isEqualTo(1);
        assertThat(userHistory.items().get(0).getUserId()).isEqualTo(userId);
        assertThat(adminHistory.total()).isEqualTo(1);
        assertThat(adminHistory.items().get(0).getCodeHint()).isEqualTo(active.getCodeHint());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.redeem(
            new GiftRedeemCommand(userId, codes.get(1),
                "revoked_request_" + suffix, now)))
            .isInstanceOf(GiftCodeRedeemException.class);
    }

    @Test
    void rotationKeepsLegacyCodesRedeemableAndIssuesOnlyWithActiveKey() {
        String originalKeyId = properties.getHmacKeyId();
        String originalSecret = properties.getHmacSecret();
        String originalVerificationKeys = properties.getHmacVerificationKeys();
        boolean originalEnabled = properties.isEnabled();
        String legacySecret = "legacy-mysql-gift-secret-at-least-32-characters";
        String activeSecret = "active-mysql-gift-secret-at-least-32-characters";
        try {
            properties.setEnabled(true);
            properties.setHmacKeyId("legacy-v1");
            properties.setHmacSecret(legacySecret);
            properties.setHmacVerificationKeys("");
            long suffix = Math.floorMod(System.nanoTime(), 800_000L);
            long userId = 99_818_000_000L + suffix;
            insertUser(userId, "gift_rotation_" + suffix);
            GiftCampaignRow campaign = activate(service.createCampaign(new GiftCampaignCommand(
                "ROTATE_" + suffix, "Chiến dịch rotation MySQL", "XU", 15, null,
                Date.from(Instant.parse("2027-01-01T00:00:00Z")),
                Date.from(Instant.parse("2027-02-01T00:00:00Z")), 10, 1)));
            String legacyCode = service.issueCodes(campaign.getId(), 1, 1).get(0);

            properties.setHmacKeyId("2027-q1");
            properties.setHmacSecret(activeSecret);
            properties.setHmacVerificationKeys("legacy-v1:" +
                Base64.getEncoder().encodeToString(
                    legacySecret.getBytes(StandardCharsets.UTF_8)));
            GiftRedemptionResult redeemed = service.redeem(new GiftRedeemCommand(
                userId, legacyCode, "rotation_" + suffix,
                Date.from(Instant.parse("2027-01-15T00:00:00Z"))));
            service.issueCodes(campaign.getId(), 1, 1);

            assertThat(redeemed.status()).isEqualTo(GiftRedemptionStatus.POSTED);
            assertThat(jdbcTemplate.queryForList("""
                SELECT hmac_key_id FROM gift_code WHERE campaign_id=? ORDER BY id
                """, String.class, campaign.getId()))
                .containsExactly("legacy-v1", "2027-q1");
        } finally {
            properties.setEnabled(originalEnabled);
            properties.setHmacKeyId(originalKeyId);
            properties.setHmacSecret(originalSecret);
            properties.setHmacVerificationKeys(originalVerificationKeys);
        }
    }

    private GiftCampaignRow activate(GiftCampaignRow campaign) {
        return service.changeCampaignStatus(campaign.getId(), campaign.getVersion(), "ACTIVE");
    }

    private void insertUser(long userId, String username) {
        jdbcTemplate.update("""
            INSERT INTO user
                (id, username, password, nick_name, account_balance, status, create_time, update_time)
            VALUES (?, ?, 'gift-code-integration-test', ?, 0, 0, NOW(), NOW())
            """, userId, username, username);
    }
}
