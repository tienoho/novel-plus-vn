package com.java2nb.novel.service.notification;

import com.java2nb.novel.FrontNovelApplication;
import io.github.xxyopen.model.page.PageBean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Date;
import java.util.List;

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
@EnabledIfSystemProperty(named = "p1.notifications.mysql.it", matches = "true")
class ChapterNotificationMySqlIntegrationTest {

    private static final long USER_BOOK_AND_AUTHOR = 9_940_001L;
    private static final long USER_AUTHOR_ONLY = 9_940_002L;
    private static final long AUTHOR_ID = 9_940_010L;
    private static final long BOOK_ID = 9_940_100L;
    private static final long APPROVED_CHAPTER = 9_940_201L;
    private static final long PENDING_CHAPTER = 9_940_202L;
    private static final long CRAWLER_CHAPTER = 9_940_203L;
    private static final long RETRY_CHAPTER = 9_940_204L;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void approvedTransitionsFanOutOnceToBookAndAuthorFollowers() {
        cleanup();
        try {
            seedUsersAndBook();
            jdbcTemplate.update("""
                INSERT INTO user_bookshelf (user_id, book_id, create_time)
                VALUES (?, ?, CURRENT_TIMESTAMP)
                """, USER_BOOK_AND_AUTHOR, BOOK_ID);
            notificationService.followAuthor(USER_BOOK_AND_AUTHOR, AUTHOR_ID);
            notificationService.followAuthor(USER_AUTHOR_ONLY, AUTHOR_ID);

            insertChapter(APPROVED_CHAPTER, "Chương đã duyệt", 1, true);
            insertChapter(PENDING_CHAPTER, "Chương chờ duyệt", 0, true);
            assertThat(eventCount(PENDING_CHAPTER)).isZero();
            jdbcTemplate.update("UPDATE book_index SET audit_status = 1 WHERE id = ?", PENDING_CHAPTER);

            process(APPROVED_CHAPTER);
            process(PENDING_CHAPTER);
            assertThat(notificationService.processEvent(eventId(APPROVED_CHAPTER))).isFalse();

            assertThat(notificationCount(USER_BOOK_AND_AUTHOR)).isEqualTo(2);
            assertThat(notificationCount(USER_AUTHOR_ONLY)).isEqualTo(2);
            assertThat(notificationService.countUnread(USER_BOOK_AND_AUTHOR)).isEqualTo(2);

            PageBean<UserNotificationRow> page = notificationService.listNotifications(
                USER_BOOK_AND_AUTHOR, 1, 1);
            assertThat(page.getTotal()).isEqualTo(2);
            assertThat(page.getList()).hasSize(1);
            long notificationId = page.getList().get(0).getId();
            notificationService.markRead(USER_BOOK_AND_AUTHOR, notificationId);
            assertThat(notificationService.countUnread(USER_BOOK_AND_AUTHOR)).isEqualTo(1);
            notificationService.markAllRead(USER_BOOK_AND_AUTHOR);
            assertThat(notificationService.countUnread(USER_BOOK_AND_AUTHOR)).isZero();

            notificationService.unfollowAuthor(USER_AUTHOR_ONLY, AUTHOR_ID);
            assertThat(notificationService.isFollowingAuthor(USER_AUTHOR_ONLY, AUTHOR_ID)).isFalse();
            insertChapter(CRAWLER_CHAPTER, "Chương crawler", null, false);
            process(CRAWLER_CHAPTER);
            assertThat(notificationCount(USER_BOOK_AND_AUTHOR)).isEqualTo(3);
            assertThat(notificationCount(USER_AUTHOR_ONLY)).isEqualTo(2);

            jdbcTemplate.update("UPDATE book_index SET audit_status = 2 WHERE id = ?", APPROVED_CHAPTER);
            jdbcTemplate.update("UPDATE book_index SET audit_status = 1 WHERE id = ?", APPROVED_CHAPTER);
            assertThat(eventCount(APPROVED_CHAPTER)).isEqualTo(1);
            assertThat(notificationCount(USER_BOOK_AND_AUTHOR)).isEqualTo(3);

            assertThatThrownBy(() -> notificationService.followAuthor(USER_AUTHOR_ONLY, 8_888_888L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Không tìm thấy tác giả");
        } finally {
            cleanup();
        }
    }

    @Test
    void failuresBackOffAndEventuallyBecomeTerminal() {
        cleanup();
        try {
            seedUsersAndBook();
            insertChapter(RETRY_CHAPTER, "Chương kiểm tra retry", 1, true);
            long eventId = eventId(RETRY_CHAPTER);

            Date firstFailureAt = new Date();
            notificationService.recordFailure(eventId, "x".repeat(600));

            assertThat(eventAttempts(eventId)).isEqualTo(1);
            assertThat(eventStatus(eventId)).isEqualTo("PENDING");
            assertThat(eventLastError(eventId)).hasSize(500).endsWith("...");
            assertThat(eventNextAttempt(eventId).getTime() - firstFailureAt.getTime())
                .isBetween(55_000L, 65_000L);
            assertThat(notificationService.listPendingEventIds(
                new Date(firstFailureAt.getTime() + 30_000L), 20)).doesNotContain(eventId);
            assertThat(notificationService.listPendingEventIds(
                new Date(firstFailureAt.getTime() + 65_000L), 20)).contains(eventId);

            for (int attempt = 2; attempt <= 9; attempt++) {
                notificationService.recordFailure(eventId, "Lỗi lần " + attempt);
            }
            Date ninthFailureNextAttempt = eventNextAttempt(eventId);
            assertThat(eventAttempts(eventId)).isEqualTo(9);
            assertThat(eventStatus(eventId)).isEqualTo("PENDING");
            assertThat(ninthFailureNextAttempt.getTime() - System.currentTimeMillis())
                .isBetween(3_590_000L, 3_605_000L);

            notificationService.recordFailure(eventId, "Lỗi lần 10");
            assertThat(eventAttempts(eventId)).isEqualTo(10);
            assertThat(eventStatus(eventId)).isEqualTo("FAILED");
            assertThat(eventLastError(eventId)).isEqualTo("Lỗi lần 10");
            assertThat(notificationService.listPendingEventIds(
                new Date(System.currentTimeMillis() + 86_400_000L), 20)).doesNotContain(eventId);
        } finally {
            cleanup();
        }
    }

    private void seedUsersAndBook() {
        jdbcTemplate.update("""
            INSERT INTO user (id, username, password, nick_name, account_balance, status, create_time, update_time)
            VALUES (?, ?, 'notification-it', 'Độc giả thông báo 1', 0, 0, NOW(), NOW()),
                   (?, ?, 'notification-it', 'Độc giả thông báo 2', 0, 0, NOW(), NOW())
            """, USER_BOOK_AND_AUTHOR, "notify_it_" + USER_BOOK_AND_AUTHOR,
            USER_AUTHOR_ONLY, "notify_it_" + USER_AUTHOR_ONLY);
        jdbcTemplate.update("""
            INSERT INTO book
                (id, work_direction, cat_id, cat_name, pic_url, book_name, author_id, author_name,
                 book_desc, score, book_status, visit_count, word_count, comment_count, yesterday_buy,
                 last_index_id, last_index_name, last_index_update_time, is_vip, status,
                 audit_status, update_time, create_time)
            VALUES (?, 0, 1, 'Thông báo', '/pic/default.png', 'Truyện thông báo IT', ?, 'Tác giả thông báo',
                    'Dữ liệu integration test', 9.0, 0, 0, 2000, 0, 0,
                    NULL, '', CURRENT_TIMESTAMP, 0, 0, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """, BOOK_ID, AUTHOR_ID);
    }

    private void insertChapter(long chapterId, String name, Integer auditStatus, boolean includeAuditStatus) {
        if (includeAuditStatus) {
            jdbcTemplate.update("""
                INSERT INTO book_index
                    (id, book_id, index_num, index_name, word_count, is_vip, book_price,
                     storage_type, audit_status, create_time, update_time)
                VALUES (?, ?, ?, ?, 1000, 0, 0, 'db', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, chapterId, BOOK_ID, (int) (chapterId - APPROVED_CHAPTER + 1), name, auditStatus);
        } else {
            jdbcTemplate.update("""
                INSERT INTO book_index
                    (id, book_id, index_num, index_name, word_count, is_vip, book_price,
                     storage_type, create_time, update_time)
                VALUES (?, ?, 3, ?, 1000, 0, 0, 'db', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, chapterId, BOOK_ID, name);
        }
    }

    private void process(long chapterId) {
        long eventId = eventId(chapterId);
        List<Long> pending = notificationService.listPendingEventIds(
            new Date(System.currentTimeMillis() + 1_000), 20);
        assertThat(pending).contains(eventId);
        assertThat(notificationService.processEvent(eventId)).isTrue();
    }

    private long eventId(long chapterId) {
        return jdbcTemplate.queryForObject(
            "SELECT id FROM chapter_publish_event WHERE chapter_id = ?", Long.class, chapterId);
    }

    private int eventCount(long chapterId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM chapter_publish_event WHERE chapter_id = ?", Integer.class, chapterId);
    }

    private int notificationCount(long userId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM user_notification WHERE user_id = ?", Integer.class, userId);
    }

    private int eventAttempts(long eventId) {
        return jdbcTemplate.queryForObject(
            "SELECT attempts FROM chapter_publish_event WHERE id = ?", Integer.class, eventId);
    }

    private String eventStatus(long eventId) {
        return jdbcTemplate.queryForObject(
            "SELECT status FROM chapter_publish_event WHERE id = ?", String.class, eventId);
    }

    private String eventLastError(long eventId) {
        return jdbcTemplate.queryForObject(
            "SELECT last_error FROM chapter_publish_event WHERE id = ?", String.class, eventId);
    }

    private Date eventNextAttempt(long eventId) {
        return jdbcTemplate.queryForObject(
            "SELECT next_attempt_time FROM chapter_publish_event WHERE id = ?", Date.class, eventId);
    }

    private void cleanup() {
        jdbcTemplate.update("DELETE FROM user_notification WHERE user_id IN (?, ?)",
            USER_BOOK_AND_AUTHOR, USER_AUTHOR_ONLY);
        jdbcTemplate.update("DELETE FROM chapter_publish_event WHERE book_id = ?", BOOK_ID);
        jdbcTemplate.update("DELETE FROM user_author_follow WHERE user_id IN (?, ?)",
            USER_BOOK_AND_AUTHOR, USER_AUTHOR_ONLY);
        jdbcTemplate.update("DELETE FROM user_bookshelf WHERE user_id IN (?, ?)",
            USER_BOOK_AND_AUTHOR, USER_AUTHOR_ONLY);
        jdbcTemplate.update("DELETE FROM book_index WHERE book_id = ?", BOOK_ID);
        jdbcTemplate.update("DELETE FROM book WHERE id = ?", BOOK_ID);
        jdbcTemplate.update("DELETE FROM user WHERE id IN (?, ?)", USER_BOOK_AND_AUTHOR, USER_AUTHOR_ONLY);
    }
}
