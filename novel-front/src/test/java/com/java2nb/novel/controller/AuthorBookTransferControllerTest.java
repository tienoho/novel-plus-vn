package com.java2nb.novel.controller;

import com.java2nb.novel.core.bean.UserDetails;
import com.java2nb.novel.core.utils.AuthCookieService;
import com.java2nb.novel.core.utils.JwtTokenUtil;
import com.java2nb.novel.entity.Author;
import com.java2nb.novel.mapper.BookOwnershipProofMapper;
import com.java2nb.novel.mapper.CopyrightAppealMapper;
import com.java2nb.novel.mapper.CopyrightReportMapper;
import com.java2nb.novel.service.AuthorChapterDraftService;
import com.java2nb.novel.service.AuthorService;
import com.java2nb.novel.service.BookService;
import com.java2nb.novel.service.ai.AuthorAiService;
import com.java2nb.novel.service.analytics.AuthorAnalyticsService;
import com.java2nb.novel.service.chapter.ChapterCommercialPolicyService;
import com.java2nb.novel.service.finance.AuthorFinanceService;
import com.java2nb.novel.service.transfer.AuthorBookTransferService;
import com.java2nb.novel.service.transfer.BookExportFile;
import com.java2nb.novel.service.transfer.BookImportResult;
import com.java2nb.novel.service.transfer.BookTransferFormat;
import io.github.xxyopen.model.resp.RestResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorBookTransferControllerTest {
    @Mock private AuthorService authorService;
    @Mock private BookService bookService;
    @Mock private AuthorChapterDraftService chapterDraftService;
    @Mock private AuthorBookTransferService bookTransferService;
    @Mock private AuthorAiService authorAiService;
    @Mock private AuthorFinanceService authorFinanceService;
    @Mock private AuthorAnalyticsService authorAnalyticsService;
    @Mock private ChapterCommercialPolicyService chapterCommercialPolicyService;
    @Mock private CopyrightAppealMapper copyrightAppealMapper;
    @Mock private BookOwnershipProofMapper bookOwnershipProofMapper;
    @Mock private CopyrightReportMapper copyrightReportMapper;
    @Mock private JwtTokenUtil jwtTokenUtil;
    @Mock private UserDetails userDetails;
    @Mock private HttpServletRequest request;

    private AuthorController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthorController(authorService, bookService, chapterDraftService,
            bookTransferService, authorAiService, authorFinanceService, authorAnalyticsService,
            chapterCommercialPolicyService, copyrightAppealMapper, bookOwnershipProofMapper,
            copyrightReportMapper);
        controller.setJwtTokenUtil(jwtTokenUtil);
        when(request.getCookies()).thenReturn(new Cookie[]{
            new Cookie(AuthCookieService.ACCESS_COOKIE, "test-token")
        });
        when(jwtTokenUtil.getUserDetailsFromToken("test-token")).thenReturn(userDetails);
        when(userDetails.getId()).thenReturn(100L);
        Author author = new Author();
        author.setId(7L);
        author.setStatus((byte) 0);
        when(authorService.queryAuthor(100L)).thenReturn(author);
    }

    @Test
    void importUsesOriginalFilenameAndAuthenticatedAuthor() {
        byte[] source = "Chương 1\nNội dung".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile upload = new MockMultipartFile(
            "file", "tác-phẩm.txt", "text/plain", source);
        BookImportResult imported = new BookImportResult(1, List.of(11L), List.of("Chương 1"));
        when(bookTransferService.importBook(7L, 9L, "tác-phẩm.txt", source)).thenReturn(imported);

        RestResult<BookImportResult> response = controller.importBook(9L, upload, request);

        assertThat(response.getData()).isSameAs(imported);
        verify(bookTransferService).importBook(7L, 9L, "tác-phẩm.txt", source);
    }

    @Test
    void exportReturnsUtf8AttachmentAndSecurityHeaders() {
        byte[] content = "nội dung".getBytes(StandardCharsets.UTF_8);
        when(bookTransferService.exportBook(7L, 9L, BookTransferFormat.DOCX)).thenReturn(
            new BookExportFile("Truyện Việt.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", content));

        ResponseEntity<byte[]> response = controller.exportBook(9L, "docx", request);

        assertThat(response.getBody()).isEqualTo(content);
        assertThat(response.getHeaders().getContentLength()).isEqualTo(content.length);
        assertThat(response.getHeaders().getContentType().toString()).contains("wordprocessingml");
        assertThat(response.getHeaders().getFirst("Content-Disposition"))
            .contains("attachment").contains("filename*=UTF-8''");
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        verify(bookTransferService).exportBook(7L, 9L, BookTransferFormat.DOCX);
    }
}
