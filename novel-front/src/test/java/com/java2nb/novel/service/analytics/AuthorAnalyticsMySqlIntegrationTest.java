package com.java2nb.novel.service.analytics;

import com.java2nb.novel.FrontNovelApplication;
import com.java2nb.novel.dto.analytics.ReaderAnalyticsEventInput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

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
@EnabledIfSystemProperty(named = "p1.analytics.mysql.it", matches = "true")
@Transactional
@Rollback
class AuthorAnalyticsMySqlIntegrationTest {

    @Autowired
    private AuthorAnalyticsService analyticsService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void eventsProduceCompletionRetentionAndHistoricalChapterRevenue() {
        long authorId = 9_920_001L;
        long bookId = 9_920_001_001L;
        long chapterOne = 9_920_001_101L;
        long chapterTwo = 9_920_001_102L;
        String prefix = "it_" + System.nanoTime();
        seedBookAndChapters(prefix, authorId, bookId, chapterOne, chapterTwo);

        ReaderAnalyticsEventInput firstStart = event(prefix + "_v1_c1_start", "v1_analytics_integration_1234567890", bookId,
            chapterOne, "START", 0, 0);
        analyticsService.recordReadEvent(firstStart);
        analyticsService.recordReadEvent(firstStart);
        analyticsService.recordReadEvent(event(prefix + "_v1_c1_complete", "v1_analytics_integration_1234567890", bookId,
            chapterOne, "COMPLETE", 95, 120));
        analyticsService.recordReadEvent(event(prefix + "_v2_c1_start", "v2_analytics_integration_1234567890", bookId,
            chapterOne, "START", 0, 0));
        analyticsService.recordReadEvent(event(prefix + "_v1_c2_start", "v1_analytics_integration_1234567890", bookId,
            chapterTwo, "START", 0, 0));

        seedPurchaseAndAuthorRevenue(prefix, bookId, chapterOne, authorId, "Chương analytics 1");

        LocalDate today = LocalDate.now();
        AuthorAnalyticsSummary summary = analyticsService.getSummary(authorId, bookId, today, today);
        AuthorAnalyticsPage page = analyticsService.getChapterAnalytics(authorId, bookId, today, today, 1, 20);
        AuthorChapterAnalyticsRow first = page.getList().stream()
            .filter(row -> row.getIndexId() == chapterOne)
            .findFirst()
            .orElseThrow();

        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM reader_chapter_event WHERE client_event_id = ?", Integer.class,
            prefix + "_v1_c1_start")).isEqualTo(1);
        assertThat(summary.getUniqueReaders()).isEqualTo(2L);
        assertThat(summary.getChapterStarts()).isEqualTo(3L);
        assertThat(summary.getChapterCompletions()).isEqualTo(1L);
        assertThat(summary.getCompletionRate()).isEqualByComparingTo(new BigDecimal("33.33"));
        assertThat(summary.getNextChapterRetentionRate()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(summary.getReadingSeconds()).isEqualTo(120L);
        assertThat(summary.getGrossRevenueXu()).isEqualTo(100L);
        assertThat(summary.getAuthorRevenueXu()).isEqualTo(70L);

        assertThat(first.getReaders()).isEqualTo(2L);
        assertThat(first.getCompletionRate()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(first.getNextChapterRetentionRate()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(first.getAverageProgress()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(first.getGrossRevenueXu()).isEqualTo(100L);
        assertThat(first.getAuthorRevenueXu()).isEqualTo(70L);

        assertThatThrownBy(() -> jdbcTemplate.update(
            "UPDATE reader_chapter_event SET progress_percent = 1 WHERE client_event_id = ?",
            prefix + "_v1_c1_start"))
            .hasMessageContaining("reader_chapter_event is immutable");
    }

    private void seedBookAndChapters(String prefix, long authorId, long bookId, long chapterOne, long chapterTwo) {
        jdbcTemplate.update("""
            INSERT INTO book
                (id, work_direction, cat_id, cat_name, pic_url, book_name, author_id, author_name,
                 book_desc, score, book_status, visit_count, word_count, comment_count, yesterday_buy,
                 last_index_id, last_index_name, last_index_update_time, is_vip, status, update_time, create_time)
            VALUES (?, 0, 1, 'Analytics', '/pic/default.png', ?, ?, 'Tác giả analytics',
                    'Truyện dùng cho integration test', 6.5, 0, 0, 2000, 0, 0,
                    ?, 'Chương analytics 2', CURRENT_TIMESTAMP, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """, bookId, "Analytics IT " + prefix, authorId, chapterTwo);
        jdbcTemplate.update("""
            INSERT INTO book_index
                (id, book_id, index_num, index_name, word_count, is_vip, book_price, storage_type,
                 create_time, update_time, audit_status)
            VALUES (?, ?, 1, 'Chương analytics 1', 1000, 0, 0, 'db', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1),
                   (?, ?, 2, 'Chương analytics 2', 1000, 0, 0, 'db', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1)
            """, chapterOne, bookId, chapterTwo, bookId);
    }

    private void seedPurchaseAndAuthorRevenue(String prefix, long bookId, long indexId, long authorId,
                                              Object indexName) {
        long readerId = 9_910_001L;
        jdbcTemplate.update("""
            INSERT INTO user_buy_record
                (user_id, book_id, book_name, book_index_id, book_index_name, buy_amount, create_time)
            VALUES (?, ?, 'Analytics IT', ?, ?, 100, CURRENT_TIMESTAMP)
            """, readerId, bookId, indexId, String.valueOf(indexName));

        jdbcTemplate.update("""
            INSERT IGNORE INTO wallet_account
                (owner_type, owner_id, account_type, currency, available_balance, pending_balance, version, status)
            VALUES ('AUTHOR', ?, 'AUTHOR_REVENUE_XU', 'XU', 0, 0, 0, 'ACTIVE'),
                   ('SYSTEM', 0, 'PLATFORM_REVENUE_XU', 'XU', 0, 0, 0, 'ACTIVE')
            """, authorId);
        long authorWallet = jdbcTemplate.queryForObject("""
            SELECT id FROM wallet_account
            WHERE owner_type = 'AUTHOR' AND owner_id = ? AND account_type = 'AUTHOR_REVENUE_XU'
            """, Long.class, authorId);
        long platformWallet = jdbcTemplate.queryForObject("""
            SELECT id FROM wallet_account
            WHERE owner_type = 'SYSTEM' AND owner_id = 0 AND account_type = 'PLATFORM_REVENUE_XU'
            """, Long.class);

        jdbcTemplate.update("""
            INSERT INTO ledger_transaction
                (transaction_no, idempotency_key, request_hash, business_type, business_id, currency,
                 total_amount, description)
            VALUES (?, ?, REPEAT('a', 64), 'CHAPTER_PURCHASE', ?, 'XU', 100, 'Analytics integration test')
            """, "TX-" + prefix, "ANALYTICS_IT:" + prefix, String.valueOf(indexId));
        long transactionId = jdbcTemplate.queryForObject(
            "SELECT id FROM ledger_transaction WHERE idempotency_key = ?", Long.class,
            "ANALYTICS_IT:" + prefix);
        jdbcTemplate.update("""
            INSERT INTO wallet_entry (ledger_transaction_id, wallet_account_id, amount, balance_after)
            VALUES (?, ?, 70, 70), (?, ?, -70, -70)
            """, transactionId, authorWallet, transactionId, platformWallet);
    }

    private ReaderAnalyticsEventInput event(String clientId, String visitorId, long bookId, long indexId,
                                            String type, int progress, int duration) {
        ReaderAnalyticsEventInput input = new ReaderAnalyticsEventInput();
        input.setClientEventId(clientId);
        input.setVisitorId(visitorId);
        input.setBookId(bookId);
        input.setIndexId(indexId);
        input.setEventType(type);
        input.setProgressPercent(progress);
        input.setDurationSeconds(duration);
        return input;
    }
}
