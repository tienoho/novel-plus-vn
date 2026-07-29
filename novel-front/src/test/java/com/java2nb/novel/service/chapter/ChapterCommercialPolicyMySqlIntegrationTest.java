package com.java2nb.novel.service.chapter;

import com.java2nb.novel.FrontNovelApplication;
import com.java2nb.novel.entity.BookIndex;
import com.java2nb.novel.mapper.ChapterCommercialPolicyMapper;
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

@SpringBootTest(classes = FrontNovelApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {"spring.task.scheduling.enabled=false", "management.health.mail.enabled=false"})
@EnabledIfSystemProperty(named = "chapter.commercial.mysql.it", matches = "true")
@Transactional
@Rollback
class ChapterCommercialPolicyMySqlIntegrationTest {
    private static final long INDEX_ID = 9_980_001L;

    @Autowired
    private ChapterCommercialPolicyMapper mapper;

    @Autowired
    private ChapterCommercialPolicyService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void policyPersistsAndTimeBoundariesMatchDatabasePrecision() {
        jdbcTemplate.update("""
            INSERT INTO book_index
                (id, book_id, index_num, index_name, word_count, is_vip, book_price, storage_type,
                 create_time, update_time, audit_status)
            VALUES (?, ?, 0, 'Chương chính sách', 1000, 1, 37, 'db', NOW(3), NOW(3), 1)
            """, INDEX_ID, 9_980_000L);
        Date from = Date.from(Instant.parse("2026-08-01T00:00:00Z"));
        Date until = Date.from(Instant.parse("2026-08-02T00:00:00Z"));
        ChapterCommercialPolicy policy = new ChapterCommercialPolicy();
        policy.setBookIndexId(INDEX_ID);
        policy.setBookId(9_980_000L);
        policy.setCustomPrice(37);
        policy.setFreeFrom(from);
        policy.setFreeUntil(until);
        assertThat(mapper.upsert(policy)).isEqualTo(1);

        ChapterCommercialPolicy stored = mapper.selectByBookIndexId(INDEX_ID);
        assertThat(stored.getCustomPrice()).isEqualTo(37);
        BookIndex chapter = new BookIndex();
        chapter.setId(INDEX_ID);
        chapter.setBookId(9_980_000L);
        chapter.setIsVip((byte) 1);
        chapter.setBookPrice(37);
        assertThat(service.evaluate(chapter, false, from).temporaryFree()).isTrue();
        assertThat(service.evaluate(chapter, false, until).purchaseRequired()).isTrue();

        assertThatThrownBy(() -> jdbcTemplate.update("""
            UPDATE chapter_commercial_policy SET free_from = free_until WHERE book_index_id = ?
            """, INDEX_ID)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("""
            UPDATE chapter_commercial_policy SET custom_price = 0 WHERE book_index_id = ?
            """, INDEX_ID)).isInstanceOf(DataAccessException.class);
    }
}
