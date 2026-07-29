package com.java2nb.novel.service.transfer;

import com.java2nb.novel.dto.author.DraftAutosaveRequest;
import com.java2nb.novel.entity.AuthorChapterDraft;
import com.java2nb.novel.entity.Book;
import com.java2nb.novel.entity.BookIndex;
import com.java2nb.novel.mapper.BookIndexMapper;
import com.java2nb.novel.service.AuthorChapterDraftService;
import com.java2nb.novel.service.BookService;
import com.java2nb.novel.service.collaboration.AuthorBookCollaborationService;
import com.java2nb.novel.service.collaboration.BookPermission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthorBookTransferServiceImplTest {
    @Mock
    private AuthorChapterDraftService chapterDraftService;
    @Mock
    private AuthorBookCollaborationService collaborationService;
    @Mock
    private BookService bookService;
    @Mock
    private BookIndexMapper bookIndexMapper;

    private AuthorBookTransferServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuthorBookTransferServiceImpl(
            chapterDraftService, collaborationService, bookService, bookIndexMapper);
    }

    @Test
    void importCreatesPrivateFreeDraftsWithStableKeys() {
        AtomicLong ids = new AtomicLong(10);
        when(chapterDraftService.autosave(eq(7L), any())).thenAnswer(invocation -> {
            DraftAutosaveRequest input = invocation.getArgument(1);
            AuthorChapterDraft draft = new AuthorChapterDraft();
            draft.setId(ids.incrementAndGet());
            draft.setIndexName(input.getIndexName());
            return draft;
        });
        byte[] source = ("Chương 1\nNội dung một\n\nChương 2\nNội dung hai")
            .getBytes(StandardCharsets.UTF_8);

        BookImportResult result = service.importBook(7L, 9L, "ban-thao.txt", source);

        assertThat(result.chapterCount()).isEqualTo(2);
        assertThat(result.draftIds()).containsExactly(11L, 12L);
        ArgumentCaptor<DraftAutosaveRequest> captor = ArgumentCaptor.forClass(DraftAutosaveRequest.class);
        verify(chapterDraftService, times(2)).autosave(eq(7L), captor.capture());
        List<DraftAutosaveRequest> requests = captor.getAllValues();
        assertThat(requests).allSatisfy(input -> {
            assertThat(input.getBookId()).isEqualTo(9L);
            assertThat(input.getIndexId()).isNull();
            assertThat(input.getIsVip()).isZero();
            assertThat(input.getCustomPrice()).isNull();
        });
        assertThat(requests).extracting(DraftAutosaveRequest::getClientKey)
            .allMatch(key -> key.matches("import-[0-9a-f]{40}-00[12]"));

        reset(chapterDraftService);
        when(chapterDraftService.autosave(eq(7L), any())).thenAnswer(invocation -> {
            DraftAutosaveRequest input = invocation.getArgument(1);
            AuthorChapterDraft draft = new AuthorChapterDraft();
            draft.setId(99L);
            draft.setIndexName(input.getIndexName());
            return draft;
        });
        service.importBook(7L, 9L, "ban-thao.txt", source);
        ArgumentCaptor<DraftAutosaveRequest> retry = ArgumentCaptor.forClass(DraftAutosaveRequest.class);
        verify(chapterDraftService, times(2)).autosave(eq(7L), retry.capture());
        assertThat(retry.getAllValues()).extracting(DraftAutosaveRequest::getClientKey)
            .containsExactlyElementsOf(requests.stream().map(DraftAutosaveRequest::getClientKey).toList());
        verify(collaborationService, times(2))
            .requirePermission(7L, 9L, BookPermission.MANAGE_CHAPTERS);
    }

    @Test
    void importStopsOnFailureAndMethodRequiresRollbackForException() throws Exception {
        AuthorChapterDraft first = new AuthorChapterDraft();
        first.setId(1L);
        first.setIndexName("Chương 1");
        when(chapterDraftService.autosave(eq(7L), any()))
            .thenReturn(first)
            .thenThrow(new IllegalStateException("Không thể lưu chương 2"));
        byte[] source = "Chương 1\nMột\nChương 2\nHai\nChương 3\nBa".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> service.importBook(7L, 9L, "book.txt", source))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("chương 2");
        verify(chapterDraftService, times(2)).autosave(eq(7L), any());

        Method method = AuthorBookTransferServiceImpl.class.getMethod(
            "importBook", long.class, long.class, String.class, byte[].class);
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.rollbackFor()).contains(Exception.class);
    }

    @Test
    void permissionFailurePreventsImportParsingAndWrites() {
        doThrow(new IllegalArgumentException("Không có quyền"))
            .when(collaborationService).requirePermission(7L, 9L, BookPermission.MANAGE_CHAPTERS);

        assertThatThrownBy(() -> service.importBook(7L, 9L, "book.txt", new byte[0]))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("quyền");
        verifyNoInteractions(chapterDraftService, bookService, bookIndexMapper);
    }

    @Test
    void exportUsesApprovedChapterOrderAndConvertsStoredHtmlToText() {
        Book book = new Book();
        book.setBookName("Truyện: Việt/Nam");
        BookIndex first = index(21L, "Chương 1");
        BookIndex second = index(22L, "Chương 2");
        when(bookService.queryBookDetail(9L)).thenReturn(book);
        when(bookIndexMapper.select(any())).thenReturn(List.of(first, second));
        when(bookService.queryIndexContent(21L, 7L))
            .thenReturn("Dòng&nbsp;một<br>Dòng &lt;hai&gt;<script>bỏ</script>");
        when(bookService.queryIndexContent(22L, 7L)).thenReturn("<p>Đoạn hai</p><p>Kết thúc</p>");

        BookExportFile exported = service.exportBook(7L, 9L, BookTransferFormat.DOCX);
        List<BookTransferChapter> parsed = new BookTransferCodec().parse(
            BookTransferFormat.DOCX, exported.content());

        assertThat(exported.filename()).isEqualTo("Truyện_ Việt_Nam.docx");
        assertThat(exported.contentType()).contains("wordprocessingml");
        assertThat(parsed).containsExactly(
            new BookTransferChapter("Chương 1", "Dòng một\nDòng <hai>"),
            new BookTransferChapter("Chương 2", "Đoạn hai\nKết thúc"));
        verify(collaborationService).requirePermission(7L, 9L, BookPermission.MANAGE_CHAPTERS);
    }

    @Test
    void exportSupportsAllFormatsAndRejectsEmptyPublishedBook() {
        Book book = new Book();
        book.setBookName("Tác phẩm");
        BookIndex chapter = index(21L, "Chương 1");
        when(bookService.queryBookDetail(9L)).thenReturn(book);
        when(bookService.queryIndexContent(21L, 7L)).thenReturn("Nội dung");
        when(bookIndexMapper.select(any())).thenReturn(List.of(chapter));

        for (BookTransferFormat format : BookTransferFormat.values()) {
            BookExportFile exported = service.exportBook(7L, 9L, format);
            assertThat(new BookTransferCodec().parse(format, exported.content()))
                .containsExactly(new BookTransferChapter("Chương 1", "Nội dung"));
        }

        when(bookIndexMapper.select(any())).thenReturn(List.of());
        assertThatThrownBy(() -> service.exportBook(7L, 9L, BookTransferFormat.TXT))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Không tìm thấy chương");
    }

    @Test
    void exportFilenameFallsBackWhenTruncationLeavesOnlyTrailingDots() {
        Book book = new Book();
        book.setBookName(".".repeat(100) + "a");
        when(bookService.queryBookDetail(9L)).thenReturn(book);
        when(bookIndexMapper.select(any())).thenReturn(List.of(index(21L, "Chương 1")));
        when(bookService.queryIndexContent(21L, 7L)).thenReturn("Nội dung");

        BookExportFile exported = service.exportBook(7L, 9L, BookTransferFormat.TXT);

        assertThat(exported.filename()).isEqualTo("tac-pham.txt");
    }

    private BookIndex index(long id, String name) {
        BookIndex index = new BookIndex();
        index.setId(id);
        index.setBookId(9L);
        index.setIndexName(name);
        index.setAuditStatus((byte) 1);
        return index;
    }
}
