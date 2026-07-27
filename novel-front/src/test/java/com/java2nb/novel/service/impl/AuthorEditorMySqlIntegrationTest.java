package com.java2nb.novel.service.impl;

import com.java2nb.novel.FrontNovelApplication;
import com.java2nb.novel.dto.author.DraftAutosaveRequest;
import com.java2nb.novel.entity.AuthorChapterDraft;
import com.java2nb.novel.service.AuthorChapterDraftService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Map;

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
@EnabledIfSystemProperty(named = "p1.mysql.it", matches = "true")
@Transactional
@Rollback
class AuthorEditorMySqlIntegrationTest {
    private static final long AUTHOR_ID = 9_910_001L;
    private static final long OTHER_AUTHOR_ID = 9_910_002L;
    private static final long BOOK_ID = 9_910_001_001L;

    @Autowired
    private AuthorChapterDraftService draftService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void draftScheduleAndVersionHistoryRemainPrivateConsistentAndIdempotent() {
        seedBook();

        AuthorChapterDraft draft = draftService.autosave(AUTHOR_ID,
            input("p1_editor_create_001", null, null, "Chương gốc", "Noi dung goc"));
        assertThat(count("SELECT COUNT(*) FROM book_index WHERE book_id = ?", BOOK_ID)).isZero();
        assertThat(count("SELECT COUNT(*) FROM author_chapter_draft WHERE id = ?", draft.getId())).isOne();
        assertThatThrownBy(() -> draftService.get(OTHER_AUTHOR_ID, draft.getId()))
            .isInstanceOf(IllegalArgumentException.class);

        DraftAutosaveRequest stale = input("p1_editor_create_001", draft.getId(), draft.getVersion() + 1,
            "Chương gốc", "Noi dung bi stale");
        assertThatThrownBy(() -> draftService.autosave(AUTHOR_ID, stale))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("phiên khác");

        Date scheduledAt = new Date(System.currentTimeMillis() + 60_000L);
        AuthorChapterDraft scheduled = draftService.schedule(AUTHOR_ID, draft.getId(), draft.getVersion(), scheduledAt);
        AuthorChapterDraft published = draftService.publishScheduled(scheduled.getId(),
            new Date(scheduledAt.getTime() + 1_000L));
        AuthorChapterDraft retried = draftService.publishScheduled(scheduled.getId(),
            new Date(scheduledAt.getTime() + 2_000L));

        assertThat(published.getStatus()).isEqualTo("PUBLISHED");
        assertThat(retried.getStatus()).isEqualTo("PUBLISHED");
        assertThat(count("SELECT COUNT(*) FROM book_index WHERE book_id = ?", BOOK_ID)).isOne();
        Long indexId = published.getPublishedIndexId();
        assertThat(indexId).isNotNull();

        AuthorChapterDraft edit = draftService.autosave(AUTHOR_ID,
            input("p1_editor_update_001", null, null, "Chương đã sửa", "Noi dung moi", indexId));
        AuthorChapterDraft updated = draftService.publishNow(AUTHOR_ID, edit.getId(), edit.getVersion());
        assertThat(updated.getPublishedIndexId()).isEqualTo(indexId);

        List<Map<String, Object>> versions = jdbcTemplate.queryForList("""
            SELECT version_num, index_name, content
            FROM book_content_history
            WHERE index_id = ?
            ORDER BY version_num
            """, indexId);
        assertThat(versions).hasSize(2);
        assertThat(versions).extracting(row -> ((Number) row.get("version_num")).intValue())
            .containsExactly(1, 2);
        assertThat(versions.get(0).get("content").toString()).contains("goc");
        assertThat(versions.get(1).get("content").toString()).contains("moi");

        Integer aggregateMismatch = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM book b
            WHERE b.id = ?
              AND b.word_count <> (SELECT COALESCE(SUM(bi.word_count), 0) FROM book_index bi WHERE bi.book_id = b.id)
            """, Integer.class, BOOK_ID);
        assertThat(aggregateMismatch).isZero();

        Long eventId = jdbcTemplate.queryForObject("""
            SELECT id FROM author_chapter_draft_event WHERE draft_id = ? ORDER BY id LIMIT 1
            """, Long.class, draft.getId());
        assertThat(eventId).isNotNull();
        assertThatThrownBy(() -> jdbcTemplate.update(
            "UPDATE author_chapter_draft_event SET detail = 'tamper' WHERE id = ?", eventId))
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining("author_chapter_draft_event is immutable");
    }

    private DraftAutosaveRequest input(String clientKey, Long draftId, Long expectedVersion,
                                       String indexName, String content) {
        return input(clientKey, draftId, expectedVersion, indexName, content, null);
    }

    private DraftAutosaveRequest input(String clientKey, Long draftId, Long expectedVersion,
                                       String indexName, String content, Long indexId) {
        DraftAutosaveRequest request = new DraftAutosaveRequest();
        request.setDraftId(draftId);
        request.setClientKey(clientKey);
        request.setBookId(BOOK_ID);
        request.setIndexId(indexId);
        request.setIndexName(indexName);
        request.setContent(content);
        request.setIsVip((byte) 0);
        request.setExpectedVersion(expectedVersion);
        return request;
    }

    private int count(String sql, Object... args) {
        Integer result = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return result == null ? 0 : result;
    }

    private void seedBook() {
        jdbcTemplate.update("""
            INSERT INTO book
                (id, pic_url, book_name, author_id, author_name, book_desc, score, word_count,
                 book_status, status, update_time, create_time)
            VALUES (?, '/pic/p1-editor.jpg', 'P1 editor integration', ?, 'P1 author',
                    'P1 editor integration', 0, 0, 0, 0, NOW(), NOW())
            """, BOOK_ID, AUTHOR_ID);
    }
}
