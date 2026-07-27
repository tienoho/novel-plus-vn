package com.java2nb.novel.service.collaboration;

import com.java2nb.novel.core.exception.BusinessException;
import com.java2nb.novel.dto.author.BookCollaboratorCreateRequest;
import com.java2nb.novel.dto.author.BookCollaboratorUpdateRequest;
import com.java2nb.novel.mapper.AuthorBookCollaborationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthorBookCollaborationServiceImplTest {
    private AuthorBookCollaborationMapper mapper;
    private AuthorBookCollaborationServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(AuthorBookCollaborationMapper.class);
        service = new AuthorBookCollaborationServiceImpl(mapper);
    }

    @Test
    void ownerHasEveryPermissionImplicitly() {
        AuthorBookAccess owner = access(7L, 10L, 7L, true);
        when(mapper.selectAccess(7L, 10L)).thenReturn(owner);

        for (BookPermission permission : BookPermission.values()) {
            assertThat(service.requirePermission(7L, 10L, permission)).isSameAs(owner);
        }
    }

    @Test
    void editorDefaultsCanManageButCannotPublishOrViewAnalytics() {
        when(mapper.selectAccess(7L, 10L)).thenReturn(access(7L, 10L, 7L, true));
        when(mapper.findActiveCandidates("editor01")).thenReturn(List.of(candidate(8L, "editor01")));
        when(mapper.selectByAuthor(10L, 8L)).thenReturn(null);
        doAnswer(invocation -> {
            AuthorBookCollaboratorRow row = invocation.getArgument(0);
            row.setId(55L);
            return 1;
        }).when(mapper).insertCollaborator(any());
        when(mapper.selectCollaborator(10L, 7L, 55L)).thenAnswer(invocation -> storedEditor());

        BookCollaboratorCreateRequest input = createInput("editor01", " editor ");
        AuthorBookCollaboratorRow result = service.add(7L, 10L, input);

        ArgumentCaptor<AuthorBookCollaboratorRow> inserted = ArgumentCaptor.forClass(AuthorBookCollaboratorRow.class);
        verify(mapper).insertCollaborator(inserted.capture());
        assertThat(inserted.getValue().getRole()).isEqualTo("EDITOR");
        assertThat(inserted.getValue().getCanEditBook()).isFalse();
        assertThat(inserted.getValue().getCanPublishBook()).isFalse();
        assertThat(inserted.getValue().getCanManageChapters()).isTrue();
        assertThat(inserted.getValue().getCanPublishChapters()).isFalse();
        assertThat(inserted.getValue().getCanManageStory()).isTrue();
        assertThat(inserted.getValue().getCanViewAnalytics()).isFalse();
        assertThat(result.getId()).isEqualTo(55L);
        verify(mapper).insertAudit(any(), org.mockito.ArgumentMatchers.eq("ADDED"), org.mockito.ArgumentMatchers.eq(7L));
    }

    @Test
    void coAuthorDefaultsToAllSixPermissions() {
        when(mapper.selectAccess(7L, 10L)).thenReturn(access(7L, 10L, 7L, true));
        when(mapper.findActiveCandidates("writer02")).thenReturn(List.of(candidate(8L, "writer02")));
        doAnswer(invocation -> {
            AuthorBookCollaboratorRow row = invocation.getArgument(0);
            row.setId(56L);
            return 1;
        }).when(mapper).insertCollaborator(any());
        AuthorBookCollaboratorRow stored = collaborator(56L, 10L, 7L, 8L, "CO_AUTHOR", 0L);
        when(mapper.selectCollaborator(10L, 7L, 56L)).thenReturn(stored);

        service.add(7L, 10L, createInput("writer02", "CO_AUTHOR"));

        ArgumentCaptor<AuthorBookCollaboratorRow> inserted = ArgumentCaptor.forClass(AuthorBookCollaboratorRow.class);
        verify(mapper).insertCollaborator(inserted.capture());
        assertThat(List.of(
            inserted.getValue().getCanEditBook(),
            inserted.getValue().getCanPublishBook(),
            inserted.getValue().getCanManageChapters(),
            inserted.getValue().getCanPublishChapters(),
            inserted.getValue().getCanManageStory(),
            inserted.getValue().getCanViewAnalytics()
        )).containsOnly(true);
    }

    @Test
    void explicitPermissionOverridesRoleDefault() {
        when(mapper.selectAccess(7L, 10L)).thenReturn(access(7L, 10L, 7L, true));
        when(mapper.findActiveCandidates("editor01")).thenReturn(List.of(candidate(8L, "editor01")));
        doAnswer(invocation -> {
            AuthorBookCollaboratorRow row = invocation.getArgument(0);
            row.setId(55L);
            return 1;
        }).when(mapper).insertCollaborator(any());
        when(mapper.selectCollaborator(10L, 7L, 55L)).thenReturn(storedEditor());
        BookCollaboratorCreateRequest input = createInput("editor01", "EDITOR");
        input.setCanPublishChapters(true);
        input.setCanManageStory(false);

        service.add(7L, 10L, input);

        ArgumentCaptor<AuthorBookCollaboratorRow> inserted = ArgumentCaptor.forClass(AuthorBookCollaboratorRow.class);
        verify(mapper).insertCollaborator(inserted.capture());
        assertThat(inserted.getValue().getCanPublishChapters()).isTrue();
        assertThat(inserted.getValue().getCanManageStory()).isFalse();
    }

    @Test
    void collaboratorCannotManageOtherCollaborators() {
        when(mapper.selectAccess(8L, 10L)).thenReturn(access(8L, 10L, 7L, false));

        assertThatThrownBy(() -> service.list(8L, 10L))
            .isInstanceOf(BusinessException.class);

        verify(mapper, never()).listCollaborators(anyLong(), anyLong());
    }

    @Test
    void selfInactiveUnknownAndAmbiguousCandidatesAreRejected() {
        when(mapper.selectAccess(7L, 10L)).thenReturn(access(7L, 10L, 7L, true));
        when(mapper.findActiveCandidates("self")).thenReturn(List.of(candidate(7L, "self")));
        when(mapper.findActiveCandidates("missing")).thenReturn(List.of());
        when(mapper.findActiveCandidates("ambiguous"))
            .thenReturn(List.of(candidate(8L, "ambiguous"), candidate(9L, "ambiguous")));

        assertThatThrownBy(() -> service.add(7L, 10L, createInput("self", "EDITOR")))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.add(7L, 10L, createInput("missing", "EDITOR")))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.add(7L, 10L, createInput("ambiguous", "EDITOR")))
            .isInstanceOf(BusinessException.class);
        verify(mapper, never()).insertCollaborator(any());
    }

    @Test
    void duplicateCollaboratorIsRejectedBeforeInsert() {
        when(mapper.selectAccess(7L, 10L)).thenReturn(access(7L, 10L, 7L, true));
        when(mapper.findActiveCandidates("editor01")).thenReturn(List.of(candidate(8L, "editor01")));
        when(mapper.selectByAuthor(10L, 8L))
            .thenReturn(collaborator(55L, 10L, 7L, 8L, "EDITOR", 0L));

        assertThatThrownBy(() -> service.add(7L, 10L, createInput("editor01", "EDITOR")))
            .isInstanceOf(BusinessException.class);

        verify(mapper, never()).insertCollaborator(any());
    }

    @Test
    void staleUpdateCannotOverwritePermissions() {
        AuthorBookCollaboratorRow current = collaborator(55L, 10L, 7L, 8L, "EDITOR", 3L);
        when(mapper.selectAccess(7L, 10L)).thenReturn(access(7L, 10L, 7L, true));
        when(mapper.selectCollaborator(10L, 7L, 55L)).thenReturn(current);
        when(mapper.updateCollaborator(any(), org.mockito.ArgumentMatchers.eq(2L),
            org.mockito.ArgumentMatchers.eq(7L))).thenReturn(0);
        BookCollaboratorUpdateRequest input = updateInput("EDITOR", 2L);

        assertThatThrownBy(() -> service.update(7L, 10L, 55L, input))
            .isInstanceOf(BusinessException.class);

        verify(mapper, never()).insertAudit(any(), any(), anyLong());
    }

    @Test
    void removeWritesSnapshotAuditAndRevocationFailsClosed() {
        AuthorBookCollaboratorRow current = collaborator(55L, 10L, 7L, 8L, "EDITOR", 3L);
        when(mapper.selectAccess(7L, 10L)).thenReturn(access(7L, 10L, 7L, true));
        when(mapper.selectCollaborator(10L, 7L, 55L)).thenReturn(current);
        when(mapper.deleteCollaborator(10L, 7L, 55L, 3L)).thenReturn(1);

        service.remove(7L, 10L, 55L, 3L);

        verify(mapper).insertAudit(current, "REMOVED", 7L);
        when(mapper.selectAccess(8L, 10L)).thenReturn(null);
        assertThatThrownBy(() -> service.requirePermission(8L, 10L, BookPermission.MANAGE_CHAPTERS))
            .isInstanceOf(BusinessException.class);
    }

    private AuthorBookAccess access(long actorId, long bookId, long ownerId, boolean owner) {
        AuthorBookAccess access = new AuthorBookAccess();
        access.setActorAuthorId(actorId);
        access.setBookId(bookId);
        access.setOwnerAuthorId(ownerId);
        access.setOwner(owner);
        access.setRole(owner ? "OWNER" : "EDITOR");
        access.setCanManageChapters(!owner);
        return access;
    }

    private AuthorBookCandidateRow candidate(long authorId, String username) {
        AuthorBookCandidateRow row = new AuthorBookCandidateRow();
        row.setAuthorId(authorId);
        row.setUserId(authorId + 100L);
        row.setUsername(username);
        row.setPenName("Bút danh " + authorId);
        return row;
    }

    private BookCollaboratorCreateRequest createInput(String username, String role) {
        BookCollaboratorCreateRequest input = new BookCollaboratorCreateRequest();
        input.setUsername(username);
        input.setRole(role);
        return input;
    }

    private BookCollaboratorUpdateRequest updateInput(String role, long version) {
        BookCollaboratorUpdateRequest input = new BookCollaboratorUpdateRequest();
        input.setRole(role);
        input.setExpectedVersion(version);
        return input;
    }

    private AuthorBookCollaboratorRow storedEditor() {
        return collaborator(55L, 10L, 7L, 8L, "EDITOR", 0L);
    }

    private AuthorBookCollaboratorRow collaborator(long id, long bookId, long ownerId,
                                                    long collaboratorId, String role, long version) {
        AuthorBookCollaboratorRow row = new AuthorBookCollaboratorRow();
        row.setId(id);
        row.setBookId(bookId);
        row.setOwnerAuthorId(ownerId);
        row.setCollaboratorAuthorId(collaboratorId);
        row.setRole(role);
        row.setVersion(version);
        row.setCanEditBook(false);
        row.setCanPublishBook(false);
        row.setCanManageChapters(true);
        row.setCanPublishChapters(false);
        row.setCanManageStory(true);
        row.setCanViewAnalytics(false);
        return row;
    }
}
