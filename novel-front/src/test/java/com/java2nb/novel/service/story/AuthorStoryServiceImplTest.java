package com.java2nb.novel.service.story;

import com.java2nb.novel.core.exception.BusinessException;
import com.java2nb.novel.dto.author.StoryItemCreateRequest;
import com.java2nb.novel.dto.author.StoryItemUpdateRequest;
import com.java2nb.novel.mapper.AuthorStoryMapper;
import com.java2nb.novel.service.collaboration.AuthorBookAccess;
import com.java2nb.novel.service.collaboration.AuthorBookCollaborationService;
import com.java2nb.novel.service.collaboration.BookPermission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthorStoryServiceImplTest {
    private AuthorStoryMapper mapper;
    private AuthorBookCollaborationService collaborationService;
    private AuthorStoryServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(AuthorStoryMapper.class);
        collaborationService = mock(AuthorBookCollaborationService.class);
        service = new AuthorStoryServiceImpl(mapper, collaborationService);
        when(collaborationService.requirePermission(anyLong(), anyLong(), eq(BookPermission.MANAGE_STORY)))
            .thenAnswer(invocation -> access(invocation.getArgument(0), invocation.getArgument(1), 7L));
    }

    @Test
    void authorCannotListAnotherAuthorsBook() {
        when(collaborationService.requirePermission(7L, 10L, BookPermission.MANAGE_STORY))
            .thenThrow(new BusinessException(com.java2nb.novel.core.enums.ResponseStatus.AUTHOR_BOOK_ACCESS_FORBIDDEN));

        assertThatThrownBy(() -> service.list(7L, 10L, "OUTLINE"))
            .isInstanceOf(BusinessException.class);

        verify(mapper, never()).list(anyLong(), anyLong(), any());
    }

    @Test
    void createNormalizesTypeAndDefaultsOrder() {
        doAnswer(invocation -> {
            AuthorStoryItemRow row = invocation.getArgument(0);
            row.setId(55L);
            return 1;
        }).when(mapper).insert(any(AuthorStoryItemRow.class));
        AuthorStoryItemRow stored = item(55L, 7L, 10L, "CHARACTER", 0L);
        when(mapper.selectOwned(7L, 55L)).thenReturn(stored);

        StoryItemCreateRequest input = new StoryItemCreateRequest();
        input.setBookId(10L);
        input.setType(" character ");
        input.setTitle("  Lan  ");
        input.setContent("Nữ chính");
        input.setTimelineLabel("  Mùa hạ  ");

        assertThat(service.create(7L, input)).isSameAs(stored);
        ArgumentCaptor<AuthorStoryItemRow> row = ArgumentCaptor.forClass(AuthorStoryItemRow.class);
        verify(mapper).insert(row.capture());
        assertThat(row.getValue().getType()).isEqualTo("CHARACTER");
        assertThat(row.getValue().getTitle()).isEqualTo("Lan");
        assertThat(row.getValue().getTimelineLabel()).isEqualTo("Mùa hạ");
        assertThat(row.getValue().getSortOrder()).isZero();
        assertThat(row.getValue().getVersion()).isZero();
    }

    @Test
    void invalidTypeIsRejectedBeforeInsert() {
        StoryItemCreateRequest input = new StoryItemCreateRequest();
        input.setBookId(10L);
        input.setType("SECRET");
        input.setTitle("Không hợp lệ");

        assertThatThrownBy(() -> service.create(7L, input))
            .isInstanceOf(BusinessException.class);
        verify(mapper, never()).insert(any());
    }

    @Test
    void staleUpdateCannotOverwriteCurrentVersion() {
        AuthorStoryItemRow current = item(55L, 7L, 10L, "OUTLINE", 3L);
        when(mapper.selectById(55L)).thenReturn(current);
        when(mapper.selectOwned(7L, 55L)).thenReturn(current);
        when(mapper.updateOwned(7L, 55L, "OUTLINE", "Mới", "Nội dung", null, 2, 2L)).thenReturn(0);

        StoryItemUpdateRequest input = new StoryItemUpdateRequest();
        input.setType("OUTLINE");
        input.setTitle("Mới");
        input.setContent("Nội dung");
        input.setSortOrder(2);
        input.setExpectedVersion(2L);

        assertThatThrownBy(() -> service.update(7L, 55L, input))
            .isInstanceOf(BusinessException.class);
        verify(mapper).selectById(55L);
        verify(mapper).selectOwned(7L, 55L);
    }

    @Test
    void deleteNeverTouchesItemOutsideCurrentAuthor() {
        when(mapper.selectById(55L)).thenReturn(null);

        assertThatThrownBy(() -> service.delete(7L, 55L, 0L))
            .isInstanceOf(BusinessException.class);

        verify(mapper, never()).deleteOwned(anyLong(), anyLong(), anyLong());
    }

    @Test
    void listNormalizesTypeBeforeQuery() {
        when(mapper.list(7L, 10L, "TIMELINE")).thenReturn(List.of(item(1L, 7L, 10L, "TIMELINE", 0L)));

        assertThat(service.list(7L, 10L, " timeline ")).hasSize(1);
        verify(mapper).list(7L, 10L, "TIMELINE");
    }

    @Test
    void collaboratorReadsAndCreatesInOwnerNamespace() {
        when(collaborationService.requirePermission(8L, 10L, BookPermission.MANAGE_STORY))
            .thenReturn(access(8L, 10L, 7L));
        when(mapper.list(7L, 10L, null)).thenReturn(List.of(item(1L, 7L, 10L, "OUTLINE", 0L)));

        assertThat(service.list(8L, 10L, null)).hasSize(1);
        verify(mapper).list(7L, 10L, null);
    }

    private AuthorBookAccess access(long actorId, long bookId, long ownerId) {
        AuthorBookAccess access = new AuthorBookAccess();
        access.setActorAuthorId(actorId);
        access.setBookId(bookId);
        access.setOwnerAuthorId(ownerId);
        access.setOwner(actorId == ownerId);
        access.setCanManageStory(true);
        return access;
    }

    private AuthorStoryItemRow item(long id, long authorId, long bookId, String type, long version) {
        AuthorStoryItemRow row = new AuthorStoryItemRow();
        row.setId(id);
        row.setAuthorId(authorId);
        row.setBookId(bookId);
        row.setType(type);
        row.setTitle("Tiêu đề");
        row.setContent("Nội dung");
        row.setSortOrder(0);
        row.setVersion(version);
        return row;
    }
}
