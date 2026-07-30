package com.java2nb.novel.service.entitlement;

import com.java2nb.novel.FrontNovelApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
    classes = FrontNovelApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.task.scheduling.enabled=false",
        "management.health.mail.enabled=false"
    }
)
@EnabledIfSystemProperty(named = "reader.ticket.mysql.it", matches = "true")
@Transactional
@Rollback
class ReadingTicketMySqlIntegrationTest {

    private static final Date AT = Date.from(Instant.parse("2099-11-15T00:00:00Z"));

    @Autowired
    private ReadingTicketService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void grantRetryCreatesOneLedgerAndOneLot() {
        long userId = uniqueId(1);
        ReadingTicketGrantCommand command = grantCommand(userId, 3, "retry", 10, 20);

        assertThat(service.grant(command)).isEqualTo(ReadingTicketPostResult.POSTED);
        assertThat(service.grant(command)).isEqualTo(ReadingTicketPostResult.ALREADY_POSTED);

        assertThat(jdbcTemplate.queryForObject(
            "SELECT available_balance FROM reading_ticket_account WHERE user_id=?",
            Long.class, userId)).isEqualTo(3L);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM reading_ticket_ledger WHERE user_id=?",
            Integer.class, userId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM reading_ticket_lot WHERE user_id=?",
            Integer.class, userId)).isEqualTo(1);
    }

    @Test
    void unlockConsumesEarliestExpiringLotAndDoesNotSpendAgain() {
        long userId = uniqueId(2);
        long bookId = uniqueId(3);
        long chapterId = uniqueId(4);
        service.grant(grantCommand(userId, 1, "later", 10, 30));
        service.grant(grantCommand(userId, 1, "earlier", 10, 20));

        ReadingTicketUnlockResult first = service.unlockChapter(new ReadingTicketUnlockCommand(
            userId, bookId, chapterId, "unlock_0001", AT, "v1", 20));
        ReadingTicketUnlockResult replay = service.unlockChapter(new ReadingTicketUnlockCommand(
            userId, bookId, chapterId, "unlock_0002", AT, "v1", 20));

        assertThat(first.status()).isEqualTo(ReadingTicketPostResult.POSTED);
        assertThat(replay.status()).isEqualTo(ReadingTicketPostResult.ALREADY_ENTITLED);
        assertThat(replay.entitlementId()).isEqualTo(first.entitlementId());
        assertThat(replay.availableBalance()).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT lot.source_ref
            FROM reading_ticket_lot_allocation allocation
            JOIN reading_ticket_lot lot ON lot.id=allocation.lot_id
            JOIN reading_ticket_ledger ledger ON ledger.id=allocation.ledger_id
            WHERE ledger.user_id=? AND ledger.entry_type='SPEND'
            """, String.class, userId)).isEqualTo("earlier");
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM reading_ticket_ledger
            WHERE user_id=? AND entry_type='SPEND'
            """, Integer.class, userId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM chapter_entitlement
            WHERE user_id=? AND book_index_id=? AND status='ACTIVE'
            """, Integer.class, userId, chapterId)).isEqualTo(1);

        assertThatThrownBy(() -> jdbcTemplate.update(
            "UPDATE reading_ticket_ledger SET amount=-2 WHERE user_id=? AND entry_type='SPEND'",
            userId))
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining("reading_ticket_ledger is immutable");
    }

    @Test
    void expiryClosesLotAndPostsImmutableLedgerExactlyOnce() {
        long userId = uniqueId(5);
        service.grant(new ReadingTicketGrantCommand(userId, 5, "SUBSCRIPTION", "expired-lot",
            "MYSQL_IT:READING_TICKET:EXPIRE:" + userId,
            new Date(AT.getTime() - 2 * 86_400_000L),
            new Date(AT.getTime() - 86_400_000L),
            "SYSTEM", null, "MySQL expiry integration test", "v1"));

        ReadingTicketExpiryResult first = service.expireDueLots(userId, AT, "v1", 100);
        ReadingTicketExpiryResult replay = service.expireDueLots(userId, AT, "v1", 100);

        assertThat(first).isEqualTo(new ReadingTicketExpiryResult(1, 5));
        assertThat(replay).isEqualTo(ReadingTicketExpiryResult.EMPTY);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT available_balance FROM reading_ticket_account WHERE user_id=?
            """, Long.class, userId)).isZero();
        assertThat(jdbcTemplate.queryForObject("""
            SELECT lifetime_expired FROM reading_ticket_account WHERE user_id=?
            """, Long.class, userId)).isEqualTo(5L);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT status FROM reading_ticket_lot WHERE user_id=?
            """, String.class, userId)).isEqualTo("EXPIRED");
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM reading_ticket_ledger
            WHERE user_id=? AND entry_type='EXPIRE' AND amount=-5
            """, Integer.class, userId)).isEqualTo(1);
    }

    @Test
    void ledgerAndLotHistoryArePaginatedAndScopedToRequestedUser() {
        long requestedUser = uniqueId(6);
        long otherUser = uniqueId(7);
        service.grant(grantCommand(requestedUser, 3, "history-owner", 10, 20));
        service.grant(grantCommand(otherUser, 4, "history-other", 10, 20));

        ReadingTicketLedgerPage ledger = service.listLedgerHistory(requestedUser, 1, 20);
        ReadingTicketLotPage lots = service.listLotHistory(requestedUser, 1, 20);

        assertThat(ledger.total()).isEqualTo(1L);
        assertThat(ledger.items()).singleElement()
            .satisfies(row -> assertThat(row.getUserId()).isEqualTo(requestedUser));
        assertThat(lots.total()).isEqualTo(1L);
        assertThat(lots.items()).singleElement().satisfies(row -> {
            assertThat(row.getUserId()).isEqualTo(requestedUser);
            assertThat(row.getGrantEntryNo()).isNotBlank();
            assertThat(row.getSourceType()).isEqualTo("SUBSCRIPTION");
        });
    }

    private ReadingTicketGrantCommand grantCommand(long userId, long amount, String sourceRef,
                                                    long effectiveDaysBefore, long expireDaysAfter) {
        return new ReadingTicketGrantCommand(userId, amount, "SUBSCRIPTION", sourceRef,
            "MYSQL_IT:READING_TICKET:" + userId + ':' + sourceRef,
            new Date(AT.getTime() - effectiveDaysBefore * 86_400_000L),
            new Date(AT.getTime() + expireDaysAfter * 86_400_000L),
            "SYSTEM", null, "MySQL integration test", "v1");
    }

    private long uniqueId(long discriminator) {
        return 99_760_000_000L + Math.floorMod(System.nanoTime(), 800_000L) * 10 + discriminator;
    }
}
