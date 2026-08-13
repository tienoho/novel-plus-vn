package com.java2nb.novel.service.reader;

import com.java2nb.novel.FrontNovelApplication;
import com.java2nb.novel.core.exception.BusinessException;
import com.java2nb.novel.dto.reader.ReaderAnnotationCreateRequest;
import com.java2nb.novel.dto.reader.ReaderAnnotationUpdateRequest;
import com.java2nb.novel.dto.reader.ReaderProgressUpdateRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

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
@EnabledIfSystemProperty(named = "reader.state.mysql.it", matches = "true")
@Transactional
@Rollback
class ReaderStateMySqlIntegrationTest {
    private static final long USER_ID = 9_973_001L;
    private static final long OTHER_USER_ID = 9_973_002L;
    private static final long AUTHOR_ID = 9_973_101L;
    private static final long BOOK_ID = 9_973_201L;
    private static final long CHAPTER_ID = 9_973_301L;

    @Autowired
    private ReaderStateService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void progressAndAnnotationsArePrivateVersionedAndIdempotent() {
        seedFixture();

        ReaderProgressRow first = service.saveProgress(USER_ID, progress(3, 120, "20.125"));
        assertThat(first.getVersion()).isZero();
        assertThat(first.getProgressPercent()).isEqualByComparingTo("20.13");
        ReaderProgressRow second = service.saveProgress(USER_ID, progress(5, 240, "54.50"));
        assertThat(second.getVersion()).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM reader_progress WHERE user_id=? AND book_id=?",
            Integer.class, USER_ID, BOOK_ID)).isEqualTo(1);

        ReaderAnnotationRow note = service.createAnnotation(USER_ID, annotation("NOTE", "Ghi chú riêng"));
        ReaderAnnotationRow bookmark = service.createAnnotation(USER_ID, annotation("BOOKMARK", null));
        assertThat(service.getState(USER_ID, BOOK_ID, CHAPTER_ID).getAnnotations())
            .extracting(ReaderAnnotationRow::getId).containsExactly(note.getId(), bookmark.getId());
        assertThat(service.getState(OTHER_USER_ID, BOOK_ID, CHAPTER_ID).getAnnotations()).isEmpty();

        ReaderAnnotationUpdateRequest update = new ReaderAnnotationUpdateRequest();
        update.setNoteText("Đã sửa");
        update.setExpectedVersion(0L);
        ReaderAnnotationRow updated = service.updateAnnotation(USER_ID, note.getId(), update);
        assertThat(updated.getVersion()).isEqualTo(1L);
        assertThat(updated.getNoteText()).isEqualTo("Đã sửa");
        assertThatThrownBy(() -> service.updateAnnotation(USER_ID, note.getId(), update))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.deleteAnnotation(OTHER_USER_ID, note.getId(), 1L))
            .isInstanceOf(BusinessException.class);

        service.deleteAnnotation(USER_ID, bookmark.getId(), 0L);
        assertThat(service.getState(USER_ID, BOOK_ID, CHAPTER_ID).getAnnotations())
            .extracting(ReaderAnnotationRow::getId).containsExactly(note.getId());

        assertThatThrownBy(() -> jdbcTemplate.update("""
            INSERT INTO reader_annotation
                (user_id, book_id, book_index_id, annotation_type, paragraph_index, character_offset)
            VALUES (?, ?, ?, 'INVALID', 0, 0)
            """, USER_ID, BOOK_ID, CHAPTER_ID))
            .isInstanceOf(DataAccessException.class);
    }

    private ReaderProgressUpdateRequest progress(int paragraph, int offset, String percent) {
        ReaderProgressUpdateRequest input = new ReaderProgressUpdateRequest();
        input.setBookId(BOOK_ID);
        input.setBookIndexId(CHAPTER_ID);
        input.setParagraphIndex(paragraph);
        input.setCharacterOffset(offset);
        input.setProgressPercent(new BigDecimal(percent));
        return input;
    }

    private ReaderAnnotationCreateRequest annotation(String type, String note) {
        ReaderAnnotationCreateRequest input = new ReaderAnnotationCreateRequest();
        input.setBookId(BOOK_ID);
        input.setBookIndexId(CHAPTER_ID);
        input.setType(type);
        input.setParagraphIndex(5);
        input.setCharacterOffset(type.equals("NOTE") ? 200 : 300);
        input.setSelectedText(type.equals("NOTE") ? "Đoạn được chọn" : "Vị trí đã đánh dấu");
        input.setNoteText(note);
        return input;
    }

    private void seedFixture() {
        insertUser(USER_ID, "reader_9973001");
        insertUser(OTHER_USER_ID, "reader_9973002");
        jdbcTemplate.update("""
            INSERT INTO author (id, user_id, pen_name, tel_phone, status, create_time)
            VALUES (?, ?, 'Tác giả reader state', '0997300101', 0, NOW())
            """, AUTHOR_ID, USER_ID);
        jdbcTemplate.update("""
            INSERT INTO book
                (id, pic_url, book_name, author_id, author_name, book_desc, score,
                 book_status, word_count, status, update_time, create_time)
            VALUES (?, '/pic/reader-state-it.png', 'Truyện reader state integration', ?,
                    'Tác giả reader state', 'Fixture có rollback', 6.5, 0, 100, 1, NOW(), NOW())
            """, BOOK_ID, AUTHOR_ID);
        jdbcTemplate.update("""
            INSERT INTO book_index
                (id, book_id, index_num, index_name, word_count, is_vip, book_price,
                 storage_type, create_time, update_time, audit_status)
            VALUES (?, ?, 1, 'Chương reader state', 100, 0, 0, 'db', NOW(), NOW(), 1)
            """, CHAPTER_ID, BOOK_ID);
    }

    private void insertUser(long id, String username) {
        jdbcTemplate.update("""
            INSERT INTO user
                (id, username, password, nick_name, account_balance, status, create_time, update_time)
            VALUES (?, ?, 'reader-state-integration-test', ?, 0, 0, NOW(), NOW())
            """, id, username, username);
    }
}
