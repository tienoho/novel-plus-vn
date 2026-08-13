package com.java2nb.novel.service.reader;

import com.java2nb.novel.core.exception.BusinessException;
import com.java2nb.novel.dto.reader.ReaderAnnotationCreateRequest;
import com.java2nb.novel.dto.reader.ReaderAnnotationUpdateRequest;
import com.java2nb.novel.dto.reader.ReaderProgressUpdateRequest;
import com.java2nb.novel.entity.Book;
import com.java2nb.novel.entity.BookIndex;
import com.java2nb.novel.entity.User;
import com.java2nb.novel.mapper.ReaderStateMapper;
import com.java2nb.novel.service.BookService;
import com.java2nb.novel.service.UserService;
import com.java2nb.novel.service.chapter.ChapterAccessDecision;
import com.java2nb.novel.service.chapter.ChapterCommercialPolicyService;
import com.java2nb.novel.service.entitlement.ReadingTicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReaderStateServiceImplTest {
    private ReaderStateMapper mapper;
    private UserService userService;
    private BookService bookService;
    private ChapterCommercialPolicyService commercialPolicyService;
    private ReadingTicketService readingTicketService;
    private ReaderStateServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(ReaderStateMapper.class);
        userService = mock(UserService.class);
        bookService = mock(BookService.class);
        commercialPolicyService = mock(ChapterCommercialPolicyService.class);
        readingTicketService = mock(ReadingTicketService.class);
        service = new ReaderStateServiceImpl(mapper, userService, bookService, commercialPolicyService,
            readingTicketService);
        when(commercialPolicyService.evaluate(any(), anyBoolean(), any()))
            .thenReturn(new ChapterAccessDecision(false, true, false, true));
        when(bookService.queryBookDetail(10L)).thenReturn(book((byte) 1, (byte) 1, (byte) 1, (byte) 0));
        when(bookService.queryBookIndex(20L)).thenReturn(chapter((byte) 0, (byte) 1));
    }

    @Test
    void stateOnlyReturnsCurrentUsersChapterData() {
        ReaderProgressRow progress = progress(7L, 10L, 20L);
        ReaderAnnotationRow annotation = annotation(31L, 7L, "BOOKMARK", 0L);
        when(mapper.selectProgress(7L, 10L)).thenReturn(progress);
        when(mapper.listAnnotations(7L, 10L, 20L)).thenReturn(List.of(annotation));

        ReaderStateView state = service.getState(7L, 10L, 20L);

        assertThat(state.getProgress()).isSameAs(progress);
        assertThat(state.getAnnotations()).containsExactly(annotation);
        verify(mapper).listAnnotations(7L, 10L, 20L);
    }

    @Test
    void vipChapterRequiresPurchase() {
        when(bookService.queryBookIndex(20L)).thenReturn(chapter((byte) 1, (byte) 1));
        when(userService.queryIsBuyBookIndex(7L, 20L)).thenReturn(false);
        when(commercialPolicyService.evaluate(any(), eq(false), any()))
            .thenReturn(new ChapterAccessDecision(true, false, false, false));

        assertThatThrownBy(() -> service.getState(7L, 10L, 20L))
            .isInstanceOf(BusinessException.class);
        verify(mapper, never()).listAnnotations(anyLong(), anyLong(), anyLong());
    }

    @Test
    void purchasedVipChapterCanExposeReaderState() {
        when(bookService.queryBookIndex(20L)).thenReturn(chapter((byte) 1, (byte) 1));
        when(userService.queryIsBuyBookIndex(7L, 20L)).thenReturn(true);
        when(commercialPolicyService.evaluate(any(), eq(true), any()))
            .thenReturn(new ChapterAccessDecision(false, false, false, false));
        when(mapper.listAnnotations(7L, 10L, 20L)).thenReturn(List.of());

        ReaderStateView state = service.getState(7L, 10L, 20L);

        assertThat(state.getAnnotations()).isEmpty();
        verify(mapper).selectProgress(7L, 10L);
        verify(mapper).listAnnotations(7L, 10L, 20L);
    }

    @Test
    void readingTicketEntitlementCanExposeVipReaderState() {
        when(bookService.queryBookIndex(20L)).thenReturn(chapter((byte) 1, (byte) 1));
        when(userService.queryIsBuyBookIndex(7L, 20L)).thenReturn(false);
        when(readingTicketService.hasActiveChapterEntitlement(eq(7L), eq(20L), any()))
            .thenReturn(true);
        when(commercialPolicyService.evaluate(any(), eq(true), any()))
            .thenReturn(new ChapterAccessDecision(false, false, false, false));
        when(mapper.listAnnotations(7L, 10L, 20L)).thenReturn(List.of());

        ReaderStateView state = service.getState(7L, 10L, 20L);

        assertThat(state.getAnnotations()).isEmpty();
        verify(commercialPolicyService).evaluate(any(), eq(true), any());
    }

    @Test
    void hiddenBookCannotExposeReaderState() {
        when(bookService.queryBookDetail(10L)).thenReturn(book((byte) 0, (byte) 1, (byte) 1, (byte) 0));

        assertThatThrownBy(() -> service.getState(7L, 10L, 20L))
            .isInstanceOf(BusinessException.class);
        verify(mapper, never()).listAnnotations(anyLong(), anyLong(), anyLong());
    }

    @Test
    void ageRestrictedBookRequiresVerifiedEligibleReader() {
        when(bookService.queryBookDetail(10L)).thenReturn(book((byte) 1, (byte) 1, (byte) 1, (byte) 18));

        assertThatThrownBy(() -> service.getState(7L, 10L, 20L))
            .isInstanceOf(BusinessException.class);
        verify(mapper, never()).listAnnotations(anyLong(), anyLong(), anyLong());
    }

    @Test
    void ageRestrictedBookAllowsVerifiedAdultReader() {
        User adult = new User();
        adult.setDateOfBirth(Date.from(Instant.parse("2000-01-01T00:00:00Z")));
        adult.setIsAgeVerified((byte) 1);
        when(userService.userInfo(7L)).thenReturn(adult);
        when(bookService.queryBookDetail(10L)).thenReturn(book((byte) 1, (byte) 1, (byte) 1, (byte) 18));
        when(mapper.listAnnotations(7L, 10L, 20L)).thenReturn(List.of());

        ReaderStateView state = service.getState(7L, 10L, 20L);

        assertThat(state.getAnnotations()).isEmpty();
        verify(mapper).listAnnotations(7L, 10L, 20L);
    }

    @Test
    void unapprovedChapterCannotExposeReaderState() {
        when(bookService.queryBookIndex(20L)).thenReturn(chapter((byte) 0, (byte) 0));

        assertThatThrownBy(() -> service.getState(7L, 10L, 20L))
            .isInstanceOf(BusinessException.class);
        verify(mapper, never()).listAnnotations(anyLong(), anyLong(), anyLong());
    }

    @Test
    void chapterFromAnotherBookCannotExposeReaderState() {
        BookIndex chapter = chapter((byte) 0, (byte) 1);
        chapter.setBookId(11L);
        when(bookService.queryBookIndex(20L)).thenReturn(chapter);

        assertThatThrownBy(() -> service.getState(7L, 10L, 20L))
            .isInstanceOf(BusinessException.class);

        verify(userService, never()).queryIsBuyBookIndex(anyLong(), anyLong());
        verify(mapper, never()).listAnnotations(anyLong(), anyLong(), anyLong());
    }

    @Test
    void progressIsRoundedAndUpsertedInUserNamespace() {
        ReaderProgressUpdateRequest input = progressInput();
        input.setProgressPercent(new BigDecimal("47.126"));
        ReaderProgressRow stored = progress(7L, 10L, 20L);
        when(mapper.selectProgress(7L, 10L)).thenReturn(stored);

        assertThat(service.saveProgress(7L, input)).isSameAs(stored);
        verify(mapper).upsertProgress(7L, 10L, 20L, 3, 125, new BigDecimal("47.13"));
    }

    @Test
    void noteIsNormalizedBeforeInsert() {
        doAnswer(invocation -> {
            ReaderAnnotationRow row = invocation.getArgument(0);
            row.setId(31L);
            return 1;
        }).when(mapper).insertAnnotation(any());
        when(mapper.selectAnnotationOwned(7L, 31L)).thenReturn(annotation(31L, 7L, "NOTE", 0L));
        ReaderAnnotationCreateRequest input = annotationInput("NOTE");
        input.setNoteText("  Ý hay  ");
        input.setSelectedText("  Đoạn trích  ");

        service.createAnnotation(7L, input);

        ArgumentCaptor<ReaderAnnotationRow> captor = ArgumentCaptor.forClass(ReaderAnnotationRow.class);
        verify(mapper).insertAnnotation(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(7L);
        assertThat(captor.getValue().getNoteText()).isEqualTo("Ý hay");
        assertThat(captor.getValue().getSelectedText()).isEqualTo("Đoạn trích");
    }

    @Test
    void noteWithoutTextIsRejected() {
        ReaderAnnotationCreateRequest input = annotationInput("NOTE");
        input.setNoteText("  ");

        assertThatThrownBy(() -> service.createAnnotation(7L, input))
            .isInstanceOf(BusinessException.class);
        verify(mapper, never()).insertAnnotation(any());
    }

    @Test
    void staleUpdateCannotOverwriteAnotherDevice() {
        ReaderAnnotationRow current = annotation(31L, 7L, "NOTE", 3L);
        when(mapper.selectAnnotationOwned(7L, 31L)).thenReturn(current);
        when(mapper.updateAnnotationOwned(7L, 31L, "Mới", 2L)).thenReturn(0);
        ReaderAnnotationUpdateRequest input = new ReaderAnnotationUpdateRequest();
        input.setNoteText("Mới");
        input.setExpectedVersion(2L);

        assertThatThrownBy(() -> service.updateAnnotation(7L, 31L, input))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void updateCannotRevealAnnotationAfterCopyrightTakedown() {
        when(mapper.selectAnnotationOwned(7L, 31L)).thenReturn(annotation(31L, 7L, "NOTE", 3L));
        when(bookService.queryBookDetail(10L)).thenReturn(book((byte) 1, (byte) 3, (byte) 1, (byte) 0));
        ReaderAnnotationUpdateRequest input = new ReaderAnnotationUpdateRequest();
        input.setNoteText("KhÃ´ng Ä‘Æ°á»£c ghi");
        input.setExpectedVersion(3L);

        assertThatThrownBy(() -> service.updateAnnotation(7L, 31L, input))
            .isInstanceOf(BusinessException.class);
        verify(mapper, never()).updateAnnotationOwned(anyLong(), anyLong(), any(), anyLong());
    }

    @Test
    void userCannotDeleteAnotherUsersAnnotation() {
        when(mapper.selectAnnotationOwned(7L, 31L)).thenReturn(null);

        assertThatThrownBy(() -> service.deleteAnnotation(7L, 31L, 0L))
            .isInstanceOf(BusinessException.class);
        verify(mapper, never()).deleteAnnotationOwned(anyLong(), anyLong(), anyLong());
    }

    @Test
    void deleteUsesExpectedVersion() {
        when(mapper.selectAnnotationOwned(7L, 31L)).thenReturn(annotation(31L, 7L, "BOOKMARK", 2L));
        when(mapper.deleteAnnotationOwned(7L, 31L, 2L)).thenReturn(1);

        service.deleteAnnotation(7L, 31L, 2L);

        verify(mapper).deleteAnnotationOwned(7L, 31L, 2L);
    }

    private Book book(byte status, byte auditStatus, byte coverAuditStatus, byte ageRating) {
        Book book = new Book();
        book.setId(10L);
        book.setStatus(status);
        book.setAuditStatus(auditStatus);
        book.setCoverAuditStatus(coverAuditStatus);
        book.setAgeRating(ageRating);
        return book;
    }

    private BookIndex chapter(byte vip, byte auditStatus) {
        BookIndex chapter = new BookIndex();
        chapter.setId(20L);
        chapter.setBookId(10L);
        chapter.setIsVip(vip);
        chapter.setAuditStatus(auditStatus);
        return chapter;
    }

    private ReaderProgressUpdateRequest progressInput() {
        ReaderProgressUpdateRequest input = new ReaderProgressUpdateRequest();
        input.setBookId(10L);
        input.setBookIndexId(20L);
        input.setParagraphIndex(3);
        input.setCharacterOffset(125);
        input.setProgressPercent(BigDecimal.ZERO);
        return input;
    }

    private ReaderAnnotationCreateRequest annotationInput(String type) {
        ReaderAnnotationCreateRequest input = new ReaderAnnotationCreateRequest();
        input.setBookId(10L);
        input.setBookIndexId(20L);
        input.setType(type);
        input.setParagraphIndex(3);
        input.setCharacterOffset(125);
        return input;
    }

    private ReaderProgressRow progress(long userId, long bookId, long bookIndexId) {
        ReaderProgressRow row = new ReaderProgressRow();
        row.setUserId(userId);
        row.setBookId(bookId);
        row.setBookIndexId(bookIndexId);
        row.setParagraphIndex(3);
        row.setCharacterOffset(125);
        row.setProgressPercent(new BigDecimal("47.13"));
        row.setVersion(1L);
        return row;
    }

    private ReaderAnnotationRow annotation(long id, long userId, String type, long version) {
        ReaderAnnotationRow row = new ReaderAnnotationRow();
        row.setId(id);
        row.setUserId(userId);
        row.setBookId(10L);
        row.setBookIndexId(20L);
        row.setType(type);
        row.setParagraphIndex(3);
        row.setCharacterOffset(125);
        row.setVersion(version);
        return row;
    }
}
