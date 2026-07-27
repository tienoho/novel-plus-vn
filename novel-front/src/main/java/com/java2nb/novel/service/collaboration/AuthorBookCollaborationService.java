package com.java2nb.novel.service.collaboration;

import com.java2nb.novel.dto.author.BookCollaboratorCreateRequest;
import com.java2nb.novel.dto.author.BookCollaboratorUpdateRequest;
import com.java2nb.novel.entity.Book;

import java.util.List;

public interface AuthorBookCollaborationService {
    AuthorBookAccess getAccess(long actorAuthorId, long bookId);

    AuthorBookAccess requirePermission(long actorAuthorId, long bookId, BookPermission permission);

    List<Book> listAccessibleBooks(long actorAuthorId);

    List<Book> listChapterManageableBooks(long actorAuthorId);

    List<AuthorBookCollaboratorRow> list(long ownerAuthorId, long bookId);

    AuthorBookCollaboratorRow add(long ownerAuthorId, long bookId, BookCollaboratorCreateRequest input);

    AuthorBookCollaboratorRow update(long ownerAuthorId, long bookId, long collaboratorId,
                                     BookCollaboratorUpdateRequest input);

    void remove(long ownerAuthorId, long bookId, long collaboratorId, long expectedVersion);
}
