package com.java2nb.novel.service.collaboration;

import com.java2nb.novel.core.enums.ResponseStatus;
import com.java2nb.novel.core.exception.BusinessException;
import com.java2nb.novel.dto.author.BookCollaboratorCreateRequest;
import com.java2nb.novel.dto.author.BookCollaboratorUpdateRequest;
import com.java2nb.novel.entity.Book;
import com.java2nb.novel.mapper.AuthorBookCollaborationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthorBookCollaborationServiceImpl implements AuthorBookCollaborationService {
    private static final String ROLE_CO_AUTHOR = "CO_AUTHOR";
    private static final String ROLE_EDITOR = "EDITOR";

    private final AuthorBookCollaborationMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public AuthorBookAccess getAccess(long actorAuthorId, long bookId) {
        return mapper.selectAccess(actorAuthorId, bookId);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthorBookAccess requirePermission(long actorAuthorId, long bookId, BookPermission permission) {
        AuthorBookAccess access = mapper.selectAccess(actorAuthorId, bookId);
        if (access == null || !access.allows(permission)) {
            throw new BusinessException(ResponseStatus.AUTHOR_BOOK_ACCESS_FORBIDDEN);
        }
        return access;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Book> listAccessibleBooks(long actorAuthorId) {
        return mapper.listAccessibleBooks(actorAuthorId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Book> listChapterManageableBooks(long actorAuthorId) {
        return mapper.listChapterManageableBooks(actorAuthorId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuthorBookCollaboratorRow> list(long ownerAuthorId, long bookId) {
        requireOwner(ownerAuthorId, bookId);
        return mapper.listCollaborators(bookId, ownerAuthorId);
    }

    @Override
    @Transactional
    public AuthorBookCollaboratorRow add(long ownerAuthorId, long bookId,
                                         BookCollaboratorCreateRequest input) {
        AuthorBookAccess ownerAccess = requireOwner(ownerAuthorId, bookId);
        List<AuthorBookCandidateRow> candidates = mapper.findActiveCandidates(normalizeIdentity(input.getUsername()));
        if (candidates.size() != 1) {
            throw new BusinessException(ResponseStatus.AUTHOR_COLLABORATOR_NOT_FOUND);
        }
        AuthorBookCandidateRow candidate = candidates.get(0);
        if (candidate.getAuthorId() == null || candidate.getAuthorId() == ownerAuthorId) {
            throw new BusinessException(ResponseStatus.AUTHOR_COLLABORATOR_INVALID);
        }
        if (mapper.selectByAuthor(bookId, candidate.getAuthorId()) != null) {
            throw new BusinessException(ResponseStatus.AUTHOR_COLLABORATOR_INVALID);
        }

        AuthorBookCollaboratorRow row = new AuthorBookCollaboratorRow();
        row.setBookId(bookId);
        row.setOwnerAuthorId(ownerAccess.getOwnerAuthorId());
        row.setCollaboratorAuthorId(candidate.getAuthorId());
        row.setCreatedByAuthorId(ownerAuthorId);
        row.setUpdatedByAuthorId(ownerAuthorId);
        row.setVersion(0L);
        applyPermissions(row, input.getRole(), input.getCanEditBook(), input.getCanPublishBook(),
            input.getCanManageChapters(), input.getCanPublishChapters(), input.getCanManageStory(),
            input.getCanViewAnalytics());
        try {
            mapper.insertCollaborator(row);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResponseStatus.AUTHOR_COLLABORATOR_INVALID);
        }
        AuthorBookCollaboratorRow stored = requireCollaborator(bookId, ownerAuthorId, row.getId());
        mapper.insertAudit(stored, "ADDED", ownerAuthorId);
        return stored;
    }

    @Override
    @Transactional
    public AuthorBookCollaboratorRow update(long ownerAuthorId, long bookId, long collaboratorId,
                                            BookCollaboratorUpdateRequest input) {
        requireOwner(ownerAuthorId, bookId);
        AuthorBookCollaboratorRow current = requireCollaborator(bookId, ownerAuthorId, collaboratorId);
        applyPermissions(current, input.getRole(), input.getCanEditBook(), input.getCanPublishBook(),
            input.getCanManageChapters(), input.getCanPublishChapters(), input.getCanManageStory(),
            input.getCanViewAnalytics());
        if (mapper.updateCollaborator(current, input.getExpectedVersion(), ownerAuthorId) != 1) {
            if (mapper.selectCollaborator(bookId, ownerAuthorId, collaboratorId) == null) {
                throw new BusinessException(ResponseStatus.AUTHOR_COLLABORATOR_NOT_FOUND);
            }
            throw new BusinessException(ResponseStatus.AUTHOR_COLLABORATION_VERSION_CONFLICT);
        }
        AuthorBookCollaboratorRow stored = requireCollaborator(bookId, ownerAuthorId, collaboratorId);
        mapper.insertAudit(stored, "UPDATED", ownerAuthorId);
        return stored;
    }

    @Override
    @Transactional
    public void remove(long ownerAuthorId, long bookId, long collaboratorId, long expectedVersion) {
        requireOwner(ownerAuthorId, bookId);
        AuthorBookCollaboratorRow current = requireCollaborator(bookId, ownerAuthorId, collaboratorId);
        if (mapper.deleteCollaborator(bookId, ownerAuthorId, collaboratorId, expectedVersion) != 1) {
            if (mapper.selectCollaborator(bookId, ownerAuthorId, collaboratorId) == null) {
                throw new BusinessException(ResponseStatus.AUTHOR_COLLABORATOR_NOT_FOUND);
            }
            throw new BusinessException(ResponseStatus.AUTHOR_COLLABORATION_VERSION_CONFLICT);
        }
        mapper.insertAudit(current, "REMOVED", ownerAuthorId);
    }

    private AuthorBookAccess requireOwner(long actorAuthorId, long bookId) {
        AuthorBookAccess access = mapper.selectAccess(actorAuthorId, bookId);
        if (access == null || !Boolean.TRUE.equals(access.getOwner())) {
            throw new BusinessException(ResponseStatus.AUTHOR_BOOK_ACCESS_FORBIDDEN);
        }
        return access;
    }

    private AuthorBookCollaboratorRow requireCollaborator(long bookId, long ownerAuthorId, long collaboratorId) {
        AuthorBookCollaboratorRow row = mapper.selectCollaborator(bookId, ownerAuthorId, collaboratorId);
        if (row == null) {
            throw new BusinessException(ResponseStatus.AUTHOR_COLLABORATOR_NOT_FOUND);
        }
        return row;
    }

    private String normalizeIdentity(String identity) {
        return identity == null ? "" : identity.trim();
    }

    private void applyPermissions(AuthorBookCollaboratorRow row, String rawRole,
                                  Boolean canEditBook, Boolean canPublishBook,
                                  Boolean canManageChapters, Boolean canPublishChapters,
                                  Boolean canManageStory, Boolean canViewAnalytics) {
        String role = rawRole == null ? "" : rawRole.trim().toUpperCase(Locale.ROOT);
        if (!ROLE_CO_AUTHOR.equals(role) && !ROLE_EDITOR.equals(role)) {
            throw new BusinessException(ResponseStatus.AUTHOR_COLLABORATOR_INVALID);
        }
        boolean coAuthor = ROLE_CO_AUTHOR.equals(role);
        row.setRole(role);
        row.setCanEditBook(valueOrDefault(canEditBook, coAuthor));
        row.setCanPublishBook(valueOrDefault(canPublishBook, coAuthor));
        row.setCanManageChapters(valueOrDefault(canManageChapters, true));
        row.setCanPublishChapters(valueOrDefault(canPublishChapters, coAuthor));
        row.setCanManageStory(valueOrDefault(canManageStory, true));
        row.setCanViewAnalytics(valueOrDefault(canViewAnalytics, coAuthor));
    }

    private boolean valueOrDefault(Boolean value, boolean defaultValue) {
        return value == null ? defaultValue : value;
    }
}
