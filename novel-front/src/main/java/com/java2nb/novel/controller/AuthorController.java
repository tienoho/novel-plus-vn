package com.java2nb.novel.controller;

import com.java2nb.novel.core.bean.UserDetails;
import com.java2nb.novel.core.enums.ResponseStatus;
import com.java2nb.novel.core.enums.CopyrightReportStatusEnum;
import com.java2nb.novel.core.utils.ContentHashUtil;
import com.java2nb.novel.dto.author.AuthorAiRequest;
import com.java2nb.novel.dto.author.DraftAutosaveRequest;
import com.java2nb.novel.dto.author.DraftScheduleRequest;
import com.java2nb.novel.entity.*;
import com.java2nb.novel.mapper.*;
import com.java2nb.novel.service.AuthorChapterDraftService;
import com.java2nb.novel.service.AuthorService;
import com.java2nb.novel.service.BookService;
import com.java2nb.novel.service.ai.AuthorAiOperation;
import com.java2nb.novel.service.ai.AuthorAiService;
import com.java2nb.novel.service.analytics.AuthorAnalyticsPage;
import com.java2nb.novel.service.analytics.AuthorAnalyticsService;
import com.java2nb.novel.service.analytics.AuthorAnalyticsSummary;
import com.java2nb.novel.service.finance.AuthorFinanceService;
import com.java2nb.novel.service.finance.AuthorKycStatus;
import com.java2nb.novel.service.finance.AuthorWithdrawalRow;
import com.java2nb.novel.service.finance.KycSubmissionRequest;
import com.java2nb.novel.service.finance.WithdrawalRequestInput;
import com.java2nb.novel.service.chapter.ChapterCommercialPolicyService;
import com.java2nb.novel.service.chapter.ChapterCommercialPolicyView;
import com.java2nb.novel.service.transfer.AuthorBookTransferService;
import com.java2nb.novel.service.transfer.BookExportFile;
import com.java2nb.novel.service.transfer.BookImportResult;
import com.java2nb.novel.service.transfer.BookTransferFormat;
import io.github.xxyopen.model.page.PageBean;
import io.github.xxyopen.model.resp.RestResult;
import io.github.xxyopen.web.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.mybatis.dynamic.sql.render.RenderingStrategies;
import org.mybatis.dynamic.sql.select.render.SelectStatementProvider;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;
import static org.mybatis.dynamic.sql.select.SelectDSL.select;

/**
 * @author 11797
 */
@RequestMapping("author")
@RestController
@Slf4j
@RequiredArgsConstructor
public class AuthorController extends BaseController {

    private final AuthorService authorService;

    private final BookService bookService;

    private final AuthorChapterDraftService chapterDraftService;

    private final AuthorBookTransferService bookTransferService;

    private final AuthorAiService authorAiService;

    private final AuthorFinanceService authorFinanceService;

    private final AuthorAnalyticsService authorAnalyticsService;

    private final ChapterCommercialPolicyService chapterCommercialPolicyService;

    private final CopyrightAppealMapper copyrightAppealMapper;

    private final BookOwnershipProofMapper bookOwnershipProofMapper;

    private final CopyrightReportMapper copyrightReportMapper;

    /** Tổng hợp lượt đọc, giữ chân và doanh thu của một truyện thuộc tác giả hiện tại. */
    @GetMapping("analytics/summary")
    public RestResult<AuthorAnalyticsSummary> getAnalyticsSummary(
        @RequestParam("bookId") long bookId,
        @RequestParam(value = "startDate", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam(value = "endDate", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
        HttpServletRequest request) {
        Author author = checkAuthor(request);
        return RestResult.ok(authorAnalyticsService.getSummary(author.getId(), bookId, startDate, endDate));
    }

    /** Phân tích theo chương, có phân trang và kiểm tra ownership ở service. */
    @GetMapping("analytics/chapters")
    public RestResult<AuthorAnalyticsPage> getChapterAnalytics(
        @RequestParam("bookId") long bookId,
        @RequestParam(value = "startDate", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam(value = "endDate", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
        @RequestParam(value = "page", defaultValue = "1") int page,
        @RequestParam(value = "limit", defaultValue = "20") int limit,
        HttpServletRequest request) {
        Author author = checkAuthor(request);
        return RestResult.ok(
            authorAnalyticsService.getChapterAnalytics(author.getId(), bookId, startDate, endDate, page, limit));
    }

    /**
     * Nộp đơn kháng nghị gỡ bỏ bản quyền
     */
    @PostMapping("copyright/appeal")
    public RestResult<Void> submitCopyrightAppeal(@RequestBody CopyrightAppeal appeal, HttpServletRequest request) {
        Author author = checkAuthor(request);
        appeal.setAuthorId(author.getId());
        appeal.setStatus((byte) 0); // 0: Chờ duyệt
        appeal.setCreateTime(new Date());
        copyrightAppealMapper.insertSelective(appeal);

        if (appeal.getReportId() != null) {
            CopyrightReport report = copyrightReportMapper.selectByPrimaryKey(appeal.getReportId()).orElse(null);
            if (report != null) {
                report.setStatus((byte) CopyrightReportStatusEnum.COUNTER_NOTICE_RECEIVED.getCode());
                report.setUpdateTime(new Date());
                copyrightReportMapper.updateByPrimaryKeySelective(report);
            }
        }
        return RestResult.ok();
    }

    /**
     * Danh sách kháng nghị của tác giả
     */
    @GetMapping("copyright/appeals")
    public RestResult<List<CopyrightAppeal>> listCopyrightAppeals(HttpServletRequest request) {
        Author author = checkAuthor(request);
        SelectStatementProvider selectStatement = select(CopyrightAppealDynamicSqlSupport.copyrightAppeal.allColumns())
            .from(CopyrightAppealDynamicSqlSupport.copyrightAppeal)
            .where(CopyrightAppealDynamicSqlSupport.authorId, isEqualTo(author.getId()))
            .orderBy(CopyrightAppealDynamicSqlSupport.createTime.descending())
            .build().render(RenderingStrategies.MYBATIS3);
        return RestResult.ok(copyrightAppealMapper.selectMany(selectStatement));
    }

    /**
     * Tải lên bằng chứng sở hữu bản quyền tác phẩm
     */
    @PostMapping("ownershipProof/upload")
    public RestResult<Void> uploadOwnershipProof(@RequestBody BookOwnershipProof proof, HttpServletRequest request) {
        Author author = checkAuthor(request);
        proof.setAuthorId(author.getId());

        String hashContent = proof.getNote() != null ? proof.getNote() : proof.getFileUrl();
        if (hashContent != null) {
            proof.setFileHash(ContentHashUtil.sha256Hex(hashContent));
        }
        proof.setVerificationStatus((byte) 0); // 0: Chờ xác thực
        proof.setCreateTime(new Date());
        bookOwnershipProofMapper.insertSelective(proof);
        return RestResult.ok();
    }

    /**
     * Truy vấn danh sách bằng chứng sở hữu của tác giả
     */
    @GetMapping("ownershipProof/list")
    public RestResult<List<BookOwnershipProof>> listOwnershipProofs(@RequestParam("bookId") Long bookId, HttpServletRequest request) {
        Author author = checkAuthor(request);
        SelectStatementProvider selectStatement = select(BookOwnershipProofDynamicSqlSupport.bookOwnershipProof.allColumns())
            .from(BookOwnershipProofDynamicSqlSupport.bookOwnershipProof)
            .where(BookOwnershipProofDynamicSqlSupport.bookId, isEqualTo(bookId))
            .and(BookOwnershipProofDynamicSqlSupport.authorId, isEqualTo(author.getId()))
            .build().render(RenderingStrategies.MYBATIS3);
        return RestResult.ok(bookOwnershipProofMapper.selectMany(selectStatement));
    }

    /**
     * Lịch sử phiên bản chỉnh sửa chương
     */
    @GetMapping("chapterHistory/{indexId}")
    public RestResult<List<BookContentHistory>> listChapterHistory(@PathVariable("indexId") Long indexId, HttpServletRequest request) {
        Author author = checkAuthor(request);
        return RestResult.ok(bookService.listChapterHistory(indexId, author.getId()));
    }

    /**
     * So sánh 2 phiên bản lịch sử chương
     */
    @GetMapping("chapterHistory/compare")
    public RestResult<Map<String, Object>> compareChapterHistory(@RequestParam("indexId") Long indexId,
        @RequestParam("v1") Integer v1, @RequestParam("v2") Integer v2, HttpServletRequest request) {
        Author author = checkAuthor(request);
        return RestResult.ok(bookService.compareChapterHistory(indexId, v1, v2, author.getId()));
    }

    /** Đọc cấu hình giá và thời gian mở khóa của một chương thuộc phạm vi tác giả. */
    @GetMapping("chapter-commercial-policy/{indexId}")
    public RestResult<ChapterCommercialPolicyView> getChapterCommercialPolicy(
        @PathVariable("indexId") Long indexId, HttpServletRequest request) {
        Author author = checkAuthor(request);
        return RestResult.ok(chapterCommercialPolicyService.getForAuthor(author.getId(), indexId));
    }

    /** Tự lưu bản nháp theo khóa idempotency của trình soạn thảo. */
    @PostMapping("drafts/autosave")
    public RestResult<AuthorChapterDraft> autosaveDraft(@RequestBody DraftAutosaveRequest input,
                                                         HttpServletRequest request) {
        Author author = checkAuthor(request);
        return RestResult.ok(chapterDraftService.autosave(author.getId(), input));
    }

    /** Nhập TXT, DOCX hoặc EPUB thành các bản nháp riêng tư; không tự xuất bản. */
    @PostMapping(value = "books/{bookId}/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RestResult<BookImportResult> importBook(
        @PathVariable("bookId") long bookId,
        @RequestPart("file") MultipartFile file,
        HttpServletRequest request) {
        Author author = checkAuthor(request);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Tệp nhập đang trống");
        }
        try {
            return RestResult.ok(bookTransferService.importBook(
                author.getId(), bookId, file.getOriginalFilename(), file.getBytes()));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Không thể đọc tệp nhập", exception);
        }
    }

    /** Xuất các chương đã duyệt theo đúng thứ tự mục lục. */
    @GetMapping("books/{bookId}/export")
    public ResponseEntity<byte[]> exportBook(
        @PathVariable("bookId") long bookId,
        @RequestParam("format") String format,
        HttpServletRequest request) {
        Author author = checkAuthor(request);
        BookExportFile file = bookTransferService.exportBook(
            author.getId(), bookId, BookTransferFormat.parse(format));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(file.contentType()));
        headers.setContentLength(file.content().length);
        headers.setContentDisposition(ContentDisposition.attachment()
            .filename(file.filename(), StandardCharsets.UTF_8).build());
        headers.setCacheControl(CacheControl.noStore());
        headers.set("X-Content-Type-Options", "nosniff");
        return ResponseEntity.ok().headers(headers).body(file.content());
    }

    /** Đọc một bản nháp thuộc tác giả hiện tại. */
    @GetMapping("drafts/{draftId}")
    public RestResult<AuthorChapterDraft> getDraft(@PathVariable("draftId") Long draftId,
                                                    HttpServletRequest request) {
        Author author = checkAuthor(request);
        return RestResult.ok(chapterDraftService.get(author.getId(), draftId));
    }

    /** Danh sách bản nháp, có thể lọc theo trạng thái. */
    @GetMapping("drafts")
    public RestResult<Map<String, Object>> listDrafts(
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "page", defaultValue = "1") int page,
        @RequestParam(value = "limit", defaultValue = "20") int pageSize,
        HttpServletRequest request) {
        Author author = checkAuthor(request);
        return RestResult.ok(Map.of(
            "items", chapterDraftService.list(author.getId(), status, page, pageSize),
            "total", chapterDraftService.count(author.getId(), status),
            "page", Math.max(page, 1),
            "limit", Math.max(1, Math.min(pageSize, 100))
        ));
    }

    /** Đặt lịch xuất bản cho một bản nháp hoàn chỉnh. */
    @PostMapping("drafts/{draftId}/schedule")
    public RestResult<AuthorChapterDraft> scheduleDraft(@PathVariable("draftId") Long draftId,
                                                         @RequestBody DraftScheduleRequest input,
                                                         HttpServletRequest request) {
        Author author = checkAuthor(request);
        if (input == null || input.getExpectedVersion() == null) {
            throw new IllegalArgumentException("Thiếu phiên bản bản nháp");
        }
        return RestResult.ok(chapterDraftService.schedule(author.getId(), draftId,
            input.getExpectedVersion(), input.getScheduledAt()));
    }

    /** Hủy lịch và đưa bản nháp về trạng thái có thể chỉnh sửa. */
    @PostMapping("drafts/{draftId}/cancel-schedule")
    public RestResult<AuthorChapterDraft> cancelDraftSchedule(@PathVariable("draftId") Long draftId,
                                                               @RequestParam("expectedVersion") Long expectedVersion,
                                                               HttpServletRequest request) {
        Author author = checkAuthor(request);
        return RestResult.ok(chapterDraftService.cancelSchedule(author.getId(), draftId, expectedVersion));
    }

    /** Xuất bản ngay; transaction đồng thời tất toán trạng thái bản nháp. */
    @PostMapping("drafts/{draftId}/publish")
    public RestResult<AuthorChapterDraft> publishDraft(@PathVariable("draftId") Long draftId,
                                                        @RequestParam("expectedVersion") Long expectedVersion,
                                                        HttpServletRequest request) {
        Author author = checkAuthor(request);
        return RestResult.ok(chapterDraftService.publishNow(author.getId(), draftId, expectedVersion));
    }

    /**
     * Kiểm tra bút danh đã tồn tại hay chưa.
     */
    @GetMapping("checkPenName")
    public RestResult<Boolean> checkPenName(String penName) {

        return RestResult.ok(authorService.checkPenName(penName));
    }

    /**
     * Truy vấn phân trang tác phẩm của tác giả.
     */
    @GetMapping("listBookByPage")
    public RestResult<PageBean<Book>> listBookByPage(@RequestParam(value = "curr", defaultValue = "1") int page,
        @RequestParam(value = "limit", defaultValue = "10") int pageSize, HttpServletRequest request) {

        return RestResult.ok(bookService.listBookPageByUserId(getUserDetails(request).getId(), page, pageSize));
    }

    /**
     * Xuất bản tác phẩm.
     */
    @PostMapping("addBook")
    public RestResult<Void> addBook(@RequestParam("bookDesc") String bookDesc, Book book, HttpServletRequest request) {

        Author author = checkAuthor(request);

        // Không nhận bookDesc qua đối tượng book để tránh tự động xóa khoảng trắng đầu dòng.
        book.setBookDesc(bookDesc
            .replaceAll("\\n", "<br>")
            .replaceAll("\\s", "&nbsp;"));
        // Xuất bản tác phẩm.
        bookService.addBook(book, author.getId(), author.getPenName());

        return RestResult.ok();
    }

    /**
     * Cập nhật trạng thái phát hành của tác phẩm.
     */
    @PostMapping("updateBookStatus")
    public RestResult<Void> updateBookStatus(Long bookId, Byte status, HttpServletRequest request) {
        Author author = checkAuthor(request);

        // Cập nhật trạng thái phát hành của tác phẩm.
        bookService.updateBookStatus(bookId, status, author.getId());

        return RestResult.ok();
    }


    /**
     * Xóa chương.
     */
    @DeleteMapping("deleteIndex/{indexId}")
    public RestResult<Void> deleteIndex(@PathVariable("indexId") Long indexId, HttpServletRequest request) {

        Author author = checkAuthor(request);

        // Xóa chương.
        bookService.deleteIndex(indexId, author.getId());

        return RestResult.ok();
    }


    /**
     * Xuất bản nội dung chương.
     */
    @PostMapping("addBookContent")
    public RestResult<Void> addBookContent(Long bookId, String indexName, String content, Byte isVip,
        HttpServletRequest request) {
        Author author = checkAuthor(request);

        content = content.replaceAll("\\n", "<br>")
            .replaceAll("\\s", "&nbsp;");
        // Xuất bản nội dung chương.
        bookService.addBookContent(bookId, indexName, content, isVip, author.getId());

        return RestResult.ok();
    }

    /**
     * Truy vấn nội dung chương.
     */
    @GetMapping("queryIndexContent/{indexId}")
    public RestResult<String> queryIndexContent(@PathVariable("indexId") Long indexId, HttpServletRequest request) {

        Author author = checkAuthor(request);

        String content = bookService.queryIndexContent(indexId, author.getId());

        content = content.replaceAll("<br>", "\n")
            .replaceAll("&nbsp;", " ");

        return RestResult.ok(content);
    }

    /**
     * Cập nhật nội dung chương.
     */
    @PostMapping("updateBookContent")
    public RestResult<Void> updateBookContent(Long indexId, String indexName, String content,
        HttpServletRequest request) {
        Author author = checkAuthor(request);

        content = content.replaceAll("\\n", "<br>")
            .replaceAll("\\s", "&nbsp;");
        // Cập nhật nội dung chương.
        bookService.updateBookContent(indexId, indexName, content, author.getId());

        return RestResult.ok();
    }

    /**
     * Cập nhật ảnh bìa tác phẩm.
     */
    @PostMapping("updateBookPic")
    public RestResult<Void> updateBookPic(@RequestParam("bookId") Long bookId, @RequestParam("bookPic") String bookPic,
        HttpServletRequest request) {
        Author author = checkAuthor(request);
        bookService.updateBookPic(bookId, bookPic, author.getId());
        return RestResult.ok();
    }


    /**
     * Truy vấn phân trang thu nhập theo ngày của tác giả.
     */
    @GetMapping("listIncomeDailyByPage")
    public RestResult<PageBean<AuthorIncomeDetail>> listIncomeDailyByPage(
        @RequestParam(value = "curr", defaultValue = "1") int page,
        @RequestParam(value = "limit", defaultValue = "10") int pageSize,
        @RequestParam(value = "bookId", defaultValue = "0") Long bookId,
        @RequestParam(value = "startTime", defaultValue = "2020-05-01") Date startTime,
        @RequestParam(value = "endTime", defaultValue = "2030-01-01") Date endTime,
        HttpServletRequest request) {

        return RestResult.ok(
            authorService.listIncomeDailyByPage(page, pageSize, getUserDetails(request).getId(), bookId, startTime,
                endTime));
    }


    /**
     * Truy vấn phân trang thu nhập theo tháng của tác giả.
     */
    @GetMapping("listIncomeMonthByPage")
    public RestResult<PageBean<AuthorIncome>> listIncomeMonthByPage(
        @RequestParam(value = "curr", defaultValue = "1") int page,
        @RequestParam(value = "limit", defaultValue = "10") int pageSize,
        @RequestParam(value = "bookId", defaultValue = "0") Long bookId,
        HttpServletRequest request) {

        return RestResult.ok(
            authorService.listIncomeMonthByPage(page, pageSize, getUserDetails(request).getId(), bookId));
    }

    /** Gửi hồ sơ KYC đã được mã hóa để chờ quản trị viên duyệt. */
    @PostMapping("finance/kyc")
    public RestResult<AuthorKycStatus> submitKyc(@RequestBody KycSubmissionRequest submission,
                                                 HttpServletRequest request) {
        Author author = checkAuthor(request);
        long userId = getUserDetails(request).getId();
        return RestResult.ok(authorFinanceService.submitKyc(author.getId(), userId, submission));
    }

    /** Xem trạng thái KYC mà không trả dữ liệu định danh rõ. */
    @GetMapping("finance/kyc")
    public RestResult<AuthorKycStatus> getKycStatus(HttpServletRequest request) {
        Author author = checkAuthor(request);
        return RestResult.ok(authorFinanceService.getKycStatus(author.getId()));
    }

    /** Xem số Xu doanh thu hiện có thể yêu cầu rút. */
    @GetMapping("finance/revenue-balance")
    public RestResult<Long> getRevenueBalance(HttpServletRequest request) {
        Author author = checkAuthor(request);
        return RestResult.ok(authorFinanceService.getAvailableRevenue(author.getId()));
    }

    /** Tạo yêu cầu rút và giữ Xu ngay trong sổ cái. */
    @PostMapping("finance/withdrawals")
    public RestResult<AuthorWithdrawalRow> requestWithdrawal(@RequestBody WithdrawalRequestInput input,
                                                              HttpServletRequest request) {
        Author author = checkAuthor(request);
        long userId = getUserDetails(request).getId();
        return RestResult.ok(authorFinanceService.requestWithdrawal(author.getId(), userId, input));
    }

    private Author checkAuthor(HttpServletRequest request) {

        UserDetails userDetails = getUserDetails(request);
        if (userDetails == null) {
            throw new BusinessException(ResponseStatus.NO_LOGIN);
        }

        // Truy vấn thông tin tác giả.
        Author author = authorService.queryAuthor(userDetails.getId());

        // Kiểm tra trạng thái tác giả.
        if (author.getStatus() == 1) {
            // Tác giả bị khóa không được xuất bản tác phẩm.
            throw new BusinessException(ResponseStatus.AUTHOR_STATUS_FORBIDDEN);
        }

        return author;


    }

    /** Truy vấn ảnh do AI tạo. */
    @GetMapping("queryAiGenPic")
    public RestResult<String> queryAiGenPic(@RequestParam("bookId") Long bookId) {
        return RestResult.ok(bookService.queryAiGenPic(bookId));
    }

    /** Mở rộng nội dung bằng AI; bản thảo chỉ được nhận trong request body. */
    @PostMapping("ai/expand")
    public RestResult<String> expandText(@Valid @RequestBody AuthorAiRequest input,
                                         HttpServletRequest request) {
        Author author = checkAuthor(request);
        return RestResult.ok(authorAiService.generate(author.getId(), AuthorAiOperation.EXPAND, input));
    }

    /** Rút gọn nội dung bằng AI. */
    @PostMapping("ai/condense")
    public RestResult<String> condenseText(@Valid @RequestBody AuthorAiRequest input,
                                           HttpServletRequest request) {
        Author author = checkAuthor(request);
        return RestResult.ok(authorAiService.generate(author.getId(), AuthorAiOperation.CONDENSE, input));
    }

    /** Viết tiếp nội dung bằng AI. */
    @PostMapping("ai/continue")
    public RestResult<String> continueText(@Valid @RequestBody AuthorAiRequest input,
                                           HttpServletRequest request) {
        Author author = checkAuthor(request);
        return RestResult.ok(authorAiService.generate(author.getId(), AuthorAiOperation.CONTINUE, input));
    }

    /** Trau chuốt nội dung bằng AI. */
    @PostMapping("ai/polish")
    public RestResult<String> polishText(@Valid @RequestBody AuthorAiRequest input,
                                         HttpServletRequest request) {
        Author author = checkAuthor(request);
        return RestResult.ok(authorAiService.generate(author.getId(), AuthorAiOperation.POLISH, input));
    }

}
