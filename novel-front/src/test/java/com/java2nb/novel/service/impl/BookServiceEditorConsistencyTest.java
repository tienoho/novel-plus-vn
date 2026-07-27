package com.java2nb.novel.service.impl;

import com.java2nb.novel.core.cache.CacheService;
import com.java2nb.novel.core.config.BookPriceProperties;
import com.java2nb.novel.core.i18n.Messages;
import com.java2nb.novel.entity.Book;
import com.java2nb.novel.entity.BookContent;
import com.java2nb.novel.entity.BookContentHistory;
import com.java2nb.novel.entity.BookIndex;
import com.java2nb.novel.mapper.*;
import com.java2nb.novel.service.AuthorService;
import com.java2nb.novel.service.FileService;
import com.java2nb.novel.service.LikeService;
import com.java2nb.novel.service.collaboration.AuthorBookAccess;
import com.java2nb.novel.service.collaboration.AuthorBookCollaborationService;
import com.java2nb.novel.service.collaboration.BookPermission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mybatis.dynamic.sql.select.render.SelectStatementProvider;
import org.mybatis.dynamic.sql.update.render.UpdateStatementProvider;
import org.springframework.ai.openai.OpenAiImageModel;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BookServiceEditorConsistencyTest {
    private FrontBookMapper bookMapper;
    private BookIndexMapper bookIndexMapper;
    private BookContentMapper bookContentMapper;
    private BookContentHistoryMapper historyMapper;
    private AuthorBookCollaborationService collaborationService;
    private BookServiceImpl service;

    @BeforeEach
    void setUp() {
        bookMapper = mock(FrontBookMapper.class);
        bookIndexMapper = mock(BookIndexMapper.class);
        bookContentMapper = mock(BookContentMapper.class);
        historyMapper = mock(BookContentHistoryMapper.class);
        collaborationService = mock(AuthorBookCollaborationService.class);
        AuthorBookAccess access = new AuthorBookAccess();
        access.setBookId(10L);
        access.setOwnerAuthorId(7L);
        access.setActorAuthorId(7L);
        access.setOwner(true);
        when(collaborationService.requirePermission(anyLong(), anyLong(), any())).thenReturn(access);

        BookPriceProperties price = new BookPriceProperties();
        price.setWordCount(BigDecimal.valueOf(1000));
        price.setValue(BigDecimal.valueOf(5));
        service = new BookServiceImpl(
            mock(Messages.class), mock(FrontBookSettingMapper.class), bookMapper,
            mock(BookCategoryMapper.class), bookIndexMapper, bookContentMapper,
            mock(FrontBookCommentMapper.class), mock(FrontBookCommentReplyMapper.class), historyMapper,
            mock(BookAuthorMapper.class), mock(CacheService.class), mock(AuthorService.class),
            collaborationService,
            mock(FileService.class), mock(LikeService.class), price, mock(OpenAiImageModel.class),
            mock(ThreadPoolExecutor.class));
    }

    @Test
    void updateLocksChapterAndBookBeforeCreatingNextHistoryVersion() {
        BookIndex index = new BookIndex();
        index.setId(20L);
        index.setBookId(10L);
        index.setWordCount(5);
        Book book = new Book();
        book.setId(10L);
        book.setAuthorId(7L);
        book.setWordCount(100);
        book.setLastIndexId(20L);

        when(bookIndexMapper.lockById(20L)).thenReturn(index);
        when(bookMapper.lockById(10L)).thenReturn(book);
        when(bookIndexMapper.selectMany(any())).thenReturn(List.of());
        BookContent existingContent = new BookContent();
        existingContent.setIndexId(20L);
        existingContent.setContent("Noi dung cu");
        when(bookContentMapper.selectMany(any(SelectStatementProvider.class)))
            .thenReturn(List.of(existingContent));
        when(historyMapper.count(any(SelectStatementProvider.class))).thenReturn(2L);
        when(historyMapper.insertSelective(any())).thenReturn(1);

        service.updateBookContent(20L, "Chương mới", "Xin chao Viet Nam", 7L);

        InOrder order = inOrder(bookIndexMapper, bookMapper, historyMapper);
        order.verify(bookIndexMapper).lockById(20L);
        order.verify(bookMapper).lockById(10L);
        order.verify(historyMapper).count(any(SelectStatementProvider.class));
        order.verify(historyMapper).insertSelective(any());

        ArgumentCaptor<BookContentHistory> history = ArgumentCaptor.forClass(BookContentHistory.class);
        verify(historyMapper).insertSelective(history.capture());
        assertThat(history.getValue().getVersionNum()).isEqualTo(3);
        assertThat(history.getValue().getWordCount()).isEqualTo(4);

        ArgumentCaptor<UpdateStatementProvider> bookUpdate =
            ArgumentCaptor.forClass(UpdateStatementProvider.class);
        verify(bookMapper).update(bookUpdate.capture());
        assertThat(bookUpdate.getValue().getParameters().values()).contains(99, "Chương mới", 10L);
    }

    @Test
    void updateRejectsActorWithoutPublishPermissionBeforeWriting() {
        BookIndex index = new BookIndex();
        index.setId(20L);
        index.setBookId(10L);
        index.setWordCount(5);
        Book book = new Book();
        book.setId(10L);
        book.setAuthorId(8L);

        when(bookIndexMapper.lockById(20L)).thenReturn(index);
        when(collaborationService.requirePermission(7L, 10L, BookPermission.PUBLISH_CHAPTERS))
            .thenThrow(new com.java2nb.novel.core.exception.BusinessException(
                com.java2nb.novel.core.enums.ResponseStatus.AUTHOR_BOOK_ACCESS_FORBIDDEN));

        assertThatThrownBy(() -> service.updateBookContent(20L, "Chương mới", "Nội dung", 7L))
            .isInstanceOf(com.java2nb.novel.core.exception.BusinessException.class);
        verify(bookIndexMapper, never()).update(any(UpdateStatementProvider.class));
        verify(bookContentMapper, never()).update(any(UpdateStatementProvider.class));
        verify(historyMapper, never()).insertSelective(any());
    }

    @Test
    void firstEditOfLegacyChapterPreservesOriginalAsVersionOne() {
        BookIndex index = new BookIndex();
        index.setId(20L);
        index.setBookId(10L);
        index.setIndexName("Chương gốc");
        index.setWordCount(3);
        Book book = new Book();
        book.setId(10L);
        book.setAuthorId(7L);
        book.setWordCount(3);
        book.setLastIndexId(20L);
        BookContent existingContent = new BookContent();
        existingContent.setIndexId(20L);
        existingContent.setContent("Noi dung cu");

        when(bookIndexMapper.lockById(20L)).thenReturn(index);
        when(bookMapper.lockById(10L)).thenReturn(book);
        when(bookContentMapper.selectMany(any(SelectStatementProvider.class)))
            .thenReturn(List.of(existingContent));
        when(bookIndexMapper.selectMany(any())).thenReturn(List.of());
        when(historyMapper.count(any(SelectStatementProvider.class))).thenReturn(0L);
        when(historyMapper.insertSelective(any())).thenReturn(1);

        service.updateBookContent(20L, "Chương mới", "Noi dung moi", 7L);

        ArgumentCaptor<BookContentHistory> histories = ArgumentCaptor.forClass(BookContentHistory.class);
        verify(historyMapper, times(2)).insertSelective(histories.capture());
        assertThat(histories.getAllValues()).extracting(BookContentHistory::getVersionNum)
            .containsExactly(1, 2);
        assertThat(histories.getAllValues()).extracting(BookContentHistory::getContent)
            .containsExactly("Noi dung cu", "Noi dung moi");
    }

    @Test
    void changingCoverReturnsItToModerationQueue() {
        service.updateBookPic(10L, "/pic/covers/new.png", 7L);

        ArgumentCaptor<UpdateStatementProvider> update =
            ArgumentCaptor.forClass(UpdateStatementProvider.class);
        verify(bookMapper).update(update.capture());

        assertThat(update.getValue().getUpdateStatement())
            .contains("cover_audit_status")
            .contains("cover_audit_reason = null");
        assertThat(update.getValue().getParameters().values())
            .contains("/pic/covers/new.png", (byte) 0, 10L);
    }
}
