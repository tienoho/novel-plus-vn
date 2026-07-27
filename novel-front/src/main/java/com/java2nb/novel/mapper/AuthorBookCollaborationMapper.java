package com.java2nb.novel.mapper;

import com.java2nb.novel.entity.Book;
import com.java2nb.novel.service.collaboration.AuthorBookAccess;
import com.java2nb.novel.service.collaboration.AuthorBookCandidateRow;
import com.java2nb.novel.service.collaboration.AuthorBookCollaboratorRow;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface AuthorBookCollaborationMapper {
    AuthorBookAccess selectAccess(@Param("actorAuthorId") long actorAuthorId,
                                  @Param("bookId") long bookId);

    List<Book> listAccessibleBooks(@Param("actorAuthorId") long actorAuthorId);

    List<Book> listChapterManageableBooks(@Param("actorAuthorId") long actorAuthorId);

    List<AuthorBookCandidateRow> findActiveCandidates(@Param("identity") String identity);

    List<AuthorBookCollaboratorRow> listCollaborators(@Param("bookId") long bookId,
                                                       @Param("ownerAuthorId") long ownerAuthorId);

    AuthorBookCollaboratorRow selectCollaborator(@Param("bookId") long bookId,
                                                  @Param("ownerAuthorId") long ownerAuthorId,
                                                  @Param("collaboratorId") long collaboratorId);

    AuthorBookCollaboratorRow selectByAuthor(@Param("bookId") long bookId,
                                              @Param("collaboratorAuthorId") long collaboratorAuthorId);

    int insertCollaborator(AuthorBookCollaboratorRow row);

    int updateCollaborator(@Param("row") AuthorBookCollaboratorRow row,
                           @Param("expectedVersion") long expectedVersion,
                           @Param("actorAuthorId") long actorAuthorId);

    int deleteCollaborator(@Param("bookId") long bookId,
                           @Param("ownerAuthorId") long ownerAuthorId,
                           @Param("collaboratorId") long collaboratorId,
                           @Param("expectedVersion") long expectedVersion);

    int insertAudit(@Param("row") AuthorBookCollaboratorRow row,
                    @Param("eventType") String eventType,
                    @Param("actorAuthorId") long actorAuthorId);
}
