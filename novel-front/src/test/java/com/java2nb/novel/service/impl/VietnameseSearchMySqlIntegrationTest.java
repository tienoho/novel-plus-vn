package com.java2nb.novel.service.impl;

import com.java2nb.novel.FrontNovelApplication;
import com.java2nb.novel.service.BookService;
import com.java2nb.novel.vo.BookSpVO;
import com.java2nb.novel.vo.BookVO;
import io.github.xxyopen.model.page.PageBean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    classes = FrontNovelApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.task.scheduling.enabled=false",
        "management.health.mail.enabled=false"
    }
)
@EnabledIfSystemProperty(named = "p1.search.mysql.it", matches = "true")
class VietnameseSearchMySqlIntegrationTest {

    private static final long BOOK_ONE = 9_930_001_001L;
    private static final long BOOK_TWO = 9_930_001_002L;
    private static final long BOOK_THREE = 9_930_001_003L;
    private static final long BOOK_FOUR = 9_930_001_004L;
    private static final long BOOK_FIVE = 9_930_001_005L;

    @Autowired
    private BookService bookService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void searchSupportsDiacriticsTyposFiltersAndPagination() {
        String suffix = Long.toString(System.nanoTime(), 36);
        deleteFixtures();
        try {
            seed(BOOK_ONE, 1, "Đắc Nhân Tâm " + suffix, "Nguyễn Hiến Lê", 100);
            seed(BOOK_TWO, 1, "Dế Mèn Phiêu Lưu Ký " + suffix, "Tô Hoài", 80);
            seed(BOOK_THREE, 2, "Cho Tôi Xin Một Vé Đi Tuổi Thơ " + suffix, "Nguyễn Nhật Ánh", 60);
            seed(BOOK_FOUR, 2, "Tuổi Trẻ Đáng Giá Bao Nhiêu " + suffix, "Rosie Nguyễn", 40);
            seed(BOOK_FIVE, 1, "Đắc Nhân Tâm Thực Hành " + suffix, "Tác giả thử nghiệm", 20);

            assertFirstBook("dac nhan tam", null, BOOK_ONE);
            assertFirstBook("Đắc Nhân Tâm", null, BOOK_ONE);
            assertFirstBook("dac nhna tam", null, BOOK_ONE);
            assertFirstBook("ngueyn nhat anh", null, BOOK_THREE);
            assertFirstBook("de men", null, BOOK_TWO);

            PageBean<?> excludedByCategory = search("dac nhna tam", 2, 1, 20);
            assertThat(excludedByCategory.getList()).isEmpty();
            assertThat(excludedByCategory.getTotal()).isZero();

            PageBean<?> firstPage = search("dac nhan tam", null, 1, 1);
            assertThat(firstPage.getPageSize()).isEqualTo(1);
            assertThat(firstPage.getList()).hasSize(1);
            assertThat(firstPage.getTotal()).isGreaterThanOrEqualTo(2);
        } finally {
            deleteFixtures();
        }
    }

    private void assertFirstBook(String keyword, Integer catId, long expectedBookId) {
        List<? extends Object> results = search(keyword, catId, 1, 20).getList();
        assertThat(results).isNotEmpty();
        assertThat(((BookVO) results.get(0)).getId()).isEqualTo(expectedBookId);
    }

    private PageBean<?> search(String keyword, Integer catId, int page, int pageSize) {
        BookSpVO params = new BookSpVO();
        params.setKeyword(keyword);
        params.setCatId(catId);
        return bookService.searchByPage(params, page, pageSize);
    }

    private void seed(long id, int catId, String bookName, String authorName, long visits) {
        jdbcTemplate.update("""
            INSERT INTO book
                (id, work_direction, cat_id, cat_name, pic_url, book_name, author_id, author_name,
                 book_desc, score, book_status, visit_count, word_count, comment_count, yesterday_buy,
                 last_index_id, last_index_name, last_index_update_time, is_vip, status, update_time, create_time,
                 age_rating, audit_status, cover_audit_status)
            VALUES (?, 0, ?, 'Tìm kiếm', '/pic/default.png', ?, ?, ?,
                    'Dữ liệu integration test tìm kiếm tiếng Việt', 9.0, 0, ?, 2000, 0, 0,
                    ?, 'Chương thử nghiệm', CURRENT_TIMESTAMP, 0, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                    0, 1, 1)
            """, id, catId, bookName, id, authorName, visits, id + 100);
    }

    private void deleteFixtures() {
        jdbcTemplate.update("DELETE FROM book WHERE id IN (?, ?, ?, ?, ?)",
            BOOK_ONE, BOOK_TWO, BOOK_THREE, BOOK_FOUR, BOOK_FIVE);
    }
}
