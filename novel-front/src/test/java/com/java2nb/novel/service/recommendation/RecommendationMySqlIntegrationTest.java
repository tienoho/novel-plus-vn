package com.java2nb.novel.service.recommendation;

import com.java2nb.novel.FrontNovelApplication;
import com.java2nb.novel.entity.Book;
import com.java2nb.novel.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    classes = FrontNovelApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.task.scheduling.enabled=false",
        "management.health.mail.enabled=false"
    }
)
@EnabledIfSystemProperty(named = "p1.recommendation.mysql.it", matches = "true")
@Transactional
@Rollback
class RecommendationMySqlIntegrationTest {

    private static final long USER_ID = 9_950_001_001L;
    private static final long CONSUMED = 9_950_002_001L;
    private static final long PERSONALIZED = 9_950_002_002L;
    private static final long POPULAR = 9_950_002_003L;
    private static final long ADULT = 9_950_002_004L;
    private static final long PENDING = 9_950_002_005L;
    private static final Set<Long> FIXTURE_BOOK_IDS = Set.of(CONSUMED, PERSONALIZED, POPULAR, ADULT, PENDING);

    @Autowired
    private RecommendationService recommendationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void recommendationPersonalizesWithoutLeakingBlockedOrUnderageBooks() {
        seedFixtures();

        List<Book> anonymous = fixtureBooks(recommendationService.recommendBooks(null, null, null, null, 10));
        assertThat(anonymous).extracting(Book::getId)
            .contains(POPULAR, PERSONALIZED, CONSUMED)
            .doesNotContain(ADULT, PENDING);
        assertThat(anonymous.get(0).getId()).isEqualTo(POPULAR);

        User unverified = profile(false);
        List<Book> member = fixtureBooks(recommendationService.recommendBooks(USER_ID, unverified, null, null, 10));
        assertThat(member.get(0).getId()).isEqualTo(PERSONALIZED);
        assertThat(member).extracting(Book::getId).doesNotContain(CONSUMED, ADULT, PENDING);

        User verifiedAdult = profile(true);
        List<Book> adult = fixtureBooks(recommendationService.recommendBooks(USER_ID, verifiedAdult, null, null, 20));
        assertThat(adult).extracting(Book::getId).contains(ADULT).doesNotContain(PENDING);

        List<Book> detail = fixtureBooks(recommendationService.recommendBooks(null, null, 1, POPULAR, 20));
        assertThat(detail.get(0).getCatId()).isEqualTo(1);
        assertThat(detail).extracting(Book::getId).doesNotContain(POPULAR, ADULT, PENDING);
    }

    private void seedFixtures() {
        jdbcTemplate.update("""
            INSERT INTO user
                (id, username, password, nick_name, account_balance, status, create_time, update_time,
                 date_of_birth, is_age_verified)
            VALUES (?, ?, 'recommendation-test', 'Recommendation test', 0, 0, NOW(), NOW(), '1990-01-01', 1)
            """, USER_ID, "recommendation_it_" + USER_ID);
        seedBook(CONSUMED, 1, "Đã đọc", 4.0, 100, 0, 1, 1);
        seedBook(PERSONALIZED, 1, "Theo sở thích", 5.0, 200, 0, 1, 1);
        seedBook(POPULAR, 2, "Phổ biến", 9.5, 10_000, 0, 1, 1);
        seedBook(ADULT, 1, "Nội dung 18+", 9.8, 20_000, 18, 1, 1);
        seedBook(PENDING, 1, "Đang kiểm duyệt", 10.0, 30_000, 0, 0, 1);
        jdbcTemplate.update("INSERT INTO user_bookshelf (user_id, book_id, create_time) VALUES (?, ?, NOW())",
            USER_ID, CONSUMED);
        jdbcTemplate.update("INSERT INTO user_read_history (user_id, book_id, create_time) VALUES (?, ?, NOW())",
            USER_ID, CONSUMED);
        jdbcTemplate.update("""
            INSERT INTO user_buy_record
                (user_id, book_id, book_name, book_index_id, book_index_name, buy_amount, create_time)
            VALUES (?, ?, 'Đã đọc', ?, 'Chương thử nghiệm', 10, NOW())
            """, USER_ID, CONSUMED, CONSUMED + 100);
    }

    private void seedBook(long id, int catId, String name, double score, long visits, int ageRating,
        int auditStatus, int coverAuditStatus) {
        jdbcTemplate.update("""
            INSERT INTO book
                (id, work_direction, cat_id, cat_name, pic_url, book_name, author_id, author_name,
                 book_desc, score, book_status, visit_count, word_count, comment_count, yesterday_buy,
                 last_index_id, last_index_name, last_index_update_time, is_vip, status, update_time, create_time,
                 age_rating, audit_status, cover_audit_status)
            VALUES (?, 0, ?, ?, '/pic/default.png', ?, ?, 'Tác giả thử nghiệm',
                    'Dữ liệu integration test recommendation', ?, 0, ?, 2000, 0, 0,
                    ?, 'Chương thử nghiệm', CURRENT_TIMESTAMP, 0, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                    ?, ?, ?)
            """, id, catId, "Thể loại " + catId, name + " " + id, id, score, visits, id + 100,
            ageRating, auditStatus, coverAuditStatus);
    }

    private User profile(boolean verified) {
        User user = new User();
        user.setDateOfBirth(Date.from(LocalDate.now().minusYears(25).atStartOfDay(ZoneId.systemDefault()).toInstant()));
        user.setIsAgeVerified(verified ? (byte) 1 : (byte) 0);
        return user;
    }

    private List<Book> fixtureBooks(List<Book> books) {
        return books.stream().filter(book -> FIXTURE_BOOK_IDS.contains(book.getId())).toList();
    }
}
