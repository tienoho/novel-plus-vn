package com.java2nb.novel.service.collaboration;

import com.java2nb.novel.FrontNovelApplication;
import com.java2nb.novel.core.exception.BusinessException;
import com.java2nb.novel.dto.author.BookCollaboratorCreateRequest;
import com.java2nb.novel.dto.author.BookCollaboratorUpdateRequest;
import com.java2nb.novel.entity.Book;
import com.java2nb.novel.mapper.AuthorChapterDraftMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

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
@EnabledIfSystemProperty(named = "collaboration.mysql.it", matches = "true")
@Transactional
@Rollback
class AuthorCollaborationMySqlIntegrationTest {
    private static final long OWNER_USER_ID = 9_971_001L;
    private static final long COLLABORATOR_USER_ID = 9_971_002L;
    private static final long OUTSIDER_USER_ID = 9_971_003L;
    private static final long OWNER_AUTHOR_ID = 9_971_101L;
    private static final long COLLABORATOR_AUTHOR_ID = 9_971_102L;
    private static final long OUTSIDER_AUTHOR_ID = 9_971_103L;
    private static final long BOOK_ID = 9_971_201L;

    @Autowired
    private AuthorBookCollaborationService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthorChapterDraftMapper draftMapper;

    @Test
    void collaborationLifecyclePreservesAuthorizationVersionAndImmutableAudit() {
        seedFixture();

        AuthorBookAccess owner = service.requirePermission(
            OWNER_AUTHOR_ID, BOOK_ID, BookPermission.VIEW_ANALYTICS);
        assertThat(owner.getOwner()).isTrue();
        assertThat(owner.getOwnerAuthorId()).isEqualTo(OWNER_AUTHOR_ID);

        BookCollaboratorCreateRequest create = new BookCollaboratorCreateRequest();
        create.setUsername("collab_9971002");
        create.setRole("CO_AUTHOR");
        AuthorBookCollaboratorRow added = service.add(OWNER_AUTHOR_ID, BOOK_ID, create);
        assertThat(added.getVersion()).isZero();
        assertThat(added.getCanPublishChapters()).isTrue();

        AuthorBookAccess collaborator = service.requirePermission(
            COLLABORATOR_AUTHOR_ID, BOOK_ID, BookPermission.PUBLISH_CHAPTERS);
        assertThat(collaborator.getOwner()).isFalse();
        assertThat(collaborator.getOwnerAuthorId()).isEqualTo(OWNER_AUTHOR_ID);
        List<Book> accessible = service.listAccessibleBooks(COLLABORATOR_AUTHOR_ID);
        assertThat(accessible).extracting(Book::getId).contains(BOOK_ID);
        assertThat(accessible.get(0).getCollaborationRole()).isEqualTo("CO_AUTHOR");
        jdbcTemplate.update("""
            INSERT INTO author_chapter_draft
                (draft_no, client_key, author_id, book_id, index_name, content, is_vip,
                 status, version, last_autosave_at)
            VALUES ('DR-COLLAB-9971201', 'collab_key_9971201', ?, ?, 'Chương cộng tác',
                    'Nội dung có rollback', 0, 'DRAFT', 0, NOW())
            """, COLLABORATOR_AUTHOR_ID, BOOK_ID);
        assertThat(draftMapper.listByAuthor(COLLABORATOR_AUTHOR_ID, null, 0, 20)).hasSize(1);
        assertThat(draftMapper.countByAuthor(COLLABORATOR_AUTHOR_ID, null)).isEqualTo(1);

        BookCollaboratorUpdateRequest update = new BookCollaboratorUpdateRequest();
        update.setRole("EDITOR");
        update.setExpectedVersion(0L);
        AuthorBookCollaboratorRow edited = service.update(
            OWNER_AUTHOR_ID, BOOK_ID, added.getId(), update);
        assertThat(edited.getVersion()).isEqualTo(1L);
        assertThat(edited.getCanManageChapters()).isTrue();
        assertThat(edited.getCanPublishChapters()).isFalse();
        assertThatThrownBy(() -> service.requirePermission(
            COLLABORATOR_AUTHOR_ID, BOOK_ID, BookPermission.PUBLISH_CHAPTERS))
            .isInstanceOf(BusinessException.class);

        assertThatThrownBy(() -> service.update(OWNER_AUTHOR_ID, BOOK_ID, added.getId(), update))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.list(OUTSIDER_AUTHOR_ID, BOOK_ID))
            .isInstanceOf(BusinessException.class);

        service.remove(OWNER_AUTHOR_ID, BOOK_ID, added.getId(), 1L);
        assertThat(service.getAccess(COLLABORATOR_AUTHOR_ID, BOOK_ID)).isNull();
        assertThat(draftMapper.listByAuthor(COLLABORATOR_AUTHOR_ID, null, 0, 20)).isEmpty();
        assertThat(draftMapper.countByAuthor(COLLABORATOR_AUTHOR_ID, null)).isZero();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM author_book_collaborator_audit WHERE book_id = ?",
            Integer.class, BOOK_ID)).isEqualTo(3);

        Long auditId = jdbcTemplate.queryForObject(
            "SELECT id FROM author_book_collaborator_audit WHERE book_id = ? ORDER BY id LIMIT 1",
            Long.class, BOOK_ID);
        assertThatThrownBy(() -> jdbcTemplate.update(
            "UPDATE author_book_collaborator_audit SET role = 'EDITOR' WHERE id = ?", auditId))
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining("author_book_collaborator_audit is immutable");
    }

    private void seedFixture() {
        insertUser(OWNER_USER_ID, "owner_9971001");
        insertUser(COLLABORATOR_USER_ID, "collab_9971002");
        insertUser(OUTSIDER_USER_ID, "outside_9971003");
        insertAuthor(OWNER_AUTHOR_ID, OWNER_USER_ID, "Chủ sở hữu");
        insertAuthor(COLLABORATOR_AUTHOR_ID, COLLABORATOR_USER_ID, "Cộng tác viên");
        insertAuthor(OUTSIDER_AUTHOR_ID, OUTSIDER_USER_ID, "Người ngoài");
        jdbcTemplate.update("""
            INSERT INTO book
                (id, pic_url, book_name, author_id, author_name, book_desc, score,
                 book_status, word_count, status, update_time, create_time)
            VALUES (?, '/pic/collaboration-it.png', 'Truyện integration cộng tác', ?,
                    'Chủ sở hữu', 'Fixture có rollback', 6.5, 0, 0, 0, NOW(), NOW())
            """, BOOK_ID, OWNER_AUTHOR_ID);
    }

    private void insertUser(long id, String username) {
        jdbcTemplate.update("""
            INSERT INTO user
                (id, username, password, nick_name, account_balance, status, create_time, update_time)
            VALUES (?, ?, 'collaboration-integration-test', ?, 0, 0, NOW(), NOW())
            """, id, username, username);
    }

    private void insertAuthor(long id, long userId, String penName) {
        jdbcTemplate.update("""
            INSERT INTO author (id, user_id, pen_name, tel_phone, status, create_time)
            VALUES (?, ?, ?, ?, 0, NOW())
            """, id, userId, penName, Long.toString(userId));
    }
}
