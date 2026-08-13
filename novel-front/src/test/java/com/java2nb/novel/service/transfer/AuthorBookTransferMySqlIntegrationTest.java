package com.java2nb.novel.service.transfer;

import com.java2nb.novel.FrontNovelApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

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
@EnabledIfSystemProperty(named = "p1.transfer.mysql.it", matches = "true")
class AuthorBookTransferMySqlIntegrationTest {
    private static final long AUTHOR_ID = 9_911_001L;
    private static final long BOOK_ID = 9_911_001_001L;

    @Autowired
    private AuthorBookTransferService transferService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void secondDraftConflictRollsBackFirstDraftAndItsAudit() throws Exception {
        jdbcTemplate.update("""
            INSERT INTO book
                (id, pic_url, book_name, author_id, author_name, book_desc, score,
                 book_status, status, update_time, create_time)
            VALUES (?, '/transfer-it.png', 'Transfer integration test', ?, 'Tác giả test', '', 0,
                    0, 0, NOW(), NOW())
            """, BOOK_ID, AUTHOR_ID);
        byte[] source = "Chương 1\nNội dung một\nChương 2\nNội dung hai".getBytes(StandardCharsets.UTF_8);
        String baseKey = importKey(BOOK_ID, source);
        jdbcTemplate.update("""
            INSERT INTO author_chapter_draft
                (draft_no, client_key, author_id, book_id, index_name, content,
                 is_vip, status, version, last_autosave_at)
            VALUES ('TRANSFER-IT-BLOCKER', ?, ?, ?, 'Xung đột', 'Payload khác',
                    0, 'DRAFT', 0, NOW(3))
            """, baseKey + "-002", AUTHOR_ID, BOOK_ID);

        assertThatThrownBy(() -> transferService.importBook(
            AUTHOR_ID, BOOK_ID, "ban-thao.txt", source))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Bản nháp đã tồn tại");

        assertThat(countDraft(baseKey + "-001")).isZero();
        assertThat(countDraft(baseKey + "-002")).isEqualTo(1);
        Integer eventCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM author_chapter_draft_event", Integer.class);
        assertThat(eventCount).isZero();
    }

    private int countDraft(String clientKey) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM author_chapter_draft WHERE author_id = ? AND client_key = ?",
            Integer.class, AUTHOR_ID, clientKey);
        return count == null ? 0 : count;
    }

    private String importKey(long bookId, byte[] source) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(Long.toString(bookId).getBytes(StandardCharsets.US_ASCII));
        digest.update((byte) 0);
        return "import-" + HexFormat.of().formatHex(digest.digest(source)).substring(0, 40);
    }
}
