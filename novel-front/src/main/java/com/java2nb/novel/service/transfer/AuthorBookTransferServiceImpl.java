package com.java2nb.novel.service.transfer;

import com.java2nb.novel.dto.author.DraftAutosaveRequest;
import com.java2nb.novel.entity.AuthorChapterDraft;
import com.java2nb.novel.entity.Book;
import com.java2nb.novel.entity.BookIndex;
import com.java2nb.novel.mapper.BookIndexDynamicSqlSupport;
import com.java2nb.novel.mapper.BookIndexMapper;
import com.java2nb.novel.service.AuthorChapterDraftService;
import com.java2nb.novel.service.BookService;
import com.java2nb.novel.service.collaboration.AuthorBookCollaborationService;
import com.java2nb.novel.service.collaboration.BookPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

@Service
@RequiredArgsConstructor
public class AuthorBookTransferServiceImpl implements AuthorBookTransferService {
    private static final Pattern SCRIPT_OR_STYLE = Pattern.compile(
        "(?is)<(script|style)[^>]*>.*?</\\1\\s*>");
    private static final Pattern LINE_BREAK = Pattern.compile("(?i)<br\\s*/?>");
    private static final Pattern BLOCK_END = Pattern.compile(
        "(?i)</(?:p|div|section|article|li|h[1-6]|blockquote|tr)\\s*>");
    private static final Pattern HTML_TAG = Pattern.compile("(?s)<[^>]+>");
    private static final Pattern INVALID_FILENAME = Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]");
    private static final Pattern TRAILING_FILENAME = Pattern.compile("[. ]+$");

    private final AuthorChapterDraftService chapterDraftService;
    private final AuthorBookCollaborationService collaborationService;
    private final BookService bookService;
    private final BookIndexMapper bookIndexMapper;
    private final BookTransferCodec codec = new BookTransferCodec();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookImportResult importBook(long authorId, long bookId, String filename, byte[] source) {
        collaborationService.requirePermission(authorId, bookId, BookPermission.MANAGE_CHAPTERS);
        BookTransferFormat format = BookTransferFormat.fromFilename(filename);
        List<BookTransferChapter> chapters = codec.parse(format, source);
        String importKey = importKey(bookId, source);
        List<Long> draftIds = new ArrayList<>(chapters.size());
        List<String> chapterNames = new ArrayList<>(chapters.size());
        for (int i = 0; i < chapters.size(); i++) {
            BookTransferChapter chapter = chapters.get(i);
            DraftAutosaveRequest request = new DraftAutosaveRequest();
            request.setClientKey(importKey + "-" + String.format("%03d", i + 1));
            request.setBookId(bookId);
            request.setIndexName(chapter.title());
            request.setContent(chapter.content());
            request.setIsVip((byte) 0);
            AuthorChapterDraft draft = chapterDraftService.autosave(authorId, request);
            draftIds.add(draft.getId());
            chapterNames.add(draft.getIndexName());
        }
        return new BookImportResult(chapters.size(), List.copyOf(draftIds), List.copyOf(chapterNames));
    }

    @Override
    public BookExportFile exportBook(long authorId, long bookId, BookTransferFormat format) {
        if (format == null) {
            throw new IllegalArgumentException("Thiếu định dạng xuất");
        }
        collaborationService.requirePermission(authorId, bookId, BookPermission.MANAGE_CHAPTERS);
        Book book = bookService.queryBookDetail(bookId);
        if (book == null) {
            throw new IllegalArgumentException("Không tìm thấy tác phẩm");
        }
        List<BookIndex> indexes = bookIndexMapper.select(c -> c
            .where(BookIndexDynamicSqlSupport.bookId, isEqualTo(bookId))
            .and(BookIndexDynamicSqlSupport.auditStatus, isEqualTo((byte) 1))
            .orderBy(BookIndexDynamicSqlSupport.indexNum, BookIndexDynamicSqlSupport.id));
        List<BookTransferChapter> chapters = indexes.stream()
            .map(index -> new BookTransferChapter(index.getIndexName(),
                toPlainText(bookService.queryIndexContent(index.getId(), authorId))))
            .toList();
        byte[] content = codec.write(format, book.getBookName(), chapters);
        String filename = safeFilename(book.getBookName()) + "." + format.extension();
        return new BookExportFile(filename, format.contentType(), content);
    }

    private String importKey(long bookId, byte[] source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(Long.toString(bookId).getBytes(StandardCharsets.US_ASCII));
            digest.update((byte) 0);
            String hash = HexFormat.of().formatHex(digest.digest(source));
            return "import-" + hash.substring(0, 40);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Máy chủ không hỗ trợ SHA-256", exception);
        }
    }

    private String toPlainText(String storedContent) {
        String content = storedContent == null ? "" : storedContent;
        content = SCRIPT_OR_STYLE.matcher(content).replaceAll("");
        content = LINE_BREAK.matcher(content).replaceAll("\n");
        content = BLOCK_END.matcher(content).replaceAll("\n");
        content = HTML_TAG.matcher(content).replaceAll("");
        content = HtmlUtils.htmlUnescape(content).replace('\u00a0', ' ');
        content = content.replace("\r\n", "\n").replace('\r', '\n');
        return content.replaceAll("[ \\t]+(?=\\n|$)", "")
            .replaceAll("\\n{3,}", "\n\n").strip();
    }

    private String safeFilename(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC);
        normalized = INVALID_FILENAME.matcher(normalized).replaceAll("_").trim();
        normalized = TRAILING_FILENAME.matcher(normalized).replaceAll("");
        if (normalized.isBlank()) normalized = "tac-pham";
        if (normalized.length() > 100) normalized = normalized.substring(0, 100).trim();
        normalized = TRAILING_FILENAME.matcher(normalized).replaceAll("");
        return normalized.isBlank() ? "tac-pham" : normalized;
    }
}
