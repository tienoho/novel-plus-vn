package com.java2nb.novel.controller;

import com.java2nb.novel.core.bean.UserDetails;
import com.java2nb.novel.common.annotation.RateLimit;
import com.java2nb.novel.core.enums.ResponseStatus;
import com.java2nb.novel.core.utils.AgeRatingUtil;
import com.java2nb.novel.core.utils.IpUtil;
import com.java2nb.novel.entity.*;
import com.java2nb.novel.dto.analytics.ReaderAnalyticsEventInput;
import com.java2nb.novel.service.*;
import com.java2nb.novel.service.analytics.AuthorAnalyticsService;
import com.java2nb.novel.vo.*;
import io.github.xxyopen.model.page.PageBean;
import io.github.xxyopen.model.page.builder.pagehelper.PageBuilder;
import io.github.xxyopen.model.resp.RestResult;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author 11797
 */
@RequestMapping("book")
@RestController
@Slf4j
@RequiredArgsConstructor
public class BookController extends BaseController {

    private final BookService bookService;

    private final Map<String, BookContentService> bookContentServiceMap;

    private final IpLocationService ipLocationService;

    private final LikeService likeService;

    private final UserService userService;

    private final AuthorAnalyticsService authorAnalyticsService;

    /**
     * Ghi sự kiện đọc chương đã ẩn danh. clientEventId bảo đảm retry không nhân đôi dữ liệu.
     */
    @PostMapping("analytics/read-event")
    @RateLimit(key = "reader-analytics", count = 120, timeWindowSeconds = 60)
    public RestResult<Void> recordReadEvent(@RequestBody ReaderAnalyticsEventInput input) {
        authorAnalyticsService.recordReadEvent(input);
        return RestResult.ok();
    }

    /**
     * Truy vấn dữ liệu cấu hình tác phẩm trang chủ
     */
    @GetMapping("listBookSetting")
    public RestResult<Map<String, List<BookSettingVO>>> listBookSetting() {
        return RestResult.ok(bookService.listBookSettingVO());
    }

    /**
     * Truy vấn dữ liệu bảng lượt xem trang chủ
     */
    @GetMapping("listClickRank")
    public RestResult<List<Book>> listClickRank() {
        return RestResult.ok(bookService.listClickRank());
    }

    /**
     * Truy vấn dữ liệu bảng tác phẩm mới trang chủ
     */
    @GetMapping("listNewRank")
    public RestResult<List<Book>> listNewRank() {
        return RestResult.ok(bookService.listNewRank());
    }

    /**
     * Truy vấn dữ liệu bảng cập nhật trang chủ
     */
    @GetMapping("listUpdateRank")
    public RestResult<List<BookVO>> listUpdateRank() {
        return RestResult.ok(bookService.listUpdateRank());
    }

    /**
     * Truy vấn danh sách danh mục tác phẩm
     */
    @GetMapping("listBookCategory")
    public RestResult<List<BookCategory>> listBookCategory() {
        return RestResult.ok(bookService.listBookCategory());
    }

    /**
     * Tìm kiếm phân trang
     */
    @GetMapping("searchByPage")
    public RestResult<?> searchByPage(@Validated BookSpVO bookSP, @RequestParam(value = "curr", defaultValue = "1") int page,
        @RequestParam(value = "limit", defaultValue = "20") int pageSize) {
        return RestResult.ok(bookService.searchByPage(bookSP, page, pageSize));
    }

    /**
     * Truy vấn thông tin chi tiết tác phẩm
     */
    @GetMapping("queryBookDetail/{id}")
    public RestResult<Book> queryBookDetail(@PathVariable("id") Long id, HttpServletRequest request) {
        Book book = bookService.queryBookDetail(id);
        ResponseStatus denialReason = AgeRatingUtil.publicBookDenialReason(book, currentUserProfile(request));
        if (denialReason != null) {
            return (RestResult) RestResult.fail(denialReason);
        }
        return RestResult.ok(book);
    }


    /**
     * Truy vấn thông tin xếp hạng tác phẩm
     */
    @GetMapping("listRank")
    public RestResult<List<Book>> listRank(@RequestParam(value = "type", defaultValue = "0") Byte type,
        @RequestParam(value = "limit", defaultValue = "30") Integer limit) {
        return RestResult.ok(bookService.listRank(type, limit));
    }

    /**
     * Tăng lượt xem
     */
    @PostMapping("addVisitCount")
    public RestResult<Void> addVisitCount(Long bookId) {
        bookService.addVisitCount(bookId, 1);
        return RestResult.ok();
    }

    /**
     * Truy vấn thông tin chương
     */
    @GetMapping("queryBookIndexAbout")
    public RestResult<Map<String, Object>> queryBookIndexAbout(Long bookId, Long lastBookIndexId,
        HttpServletRequest request) {
        Book book = bookService.queryBookDetail(bookId);
        ResponseStatus bookDenial = AgeRatingUtil.publicBookDenialReason(book, currentUserProfile(request));
        if (bookDenial != null) {
            return (RestResult) RestResult.fail(bookDenial);
        }
        Map<String, Object> data = new HashMap<>(2);
        data.put("bookIndexCount", bookService.queryIndexCount(bookId));
        BookIndex bookIndex = bookService.queryBookIndex(lastBookIndexId);
        ResponseStatus chapterDenial = AgeRatingUtil.publicChapterDenialReason(bookIndex, bookId);
        if (chapterDenial != null) {
            return (RestResult) RestResult.fail(chapterDenial);
        }
        String lastBookContent = bookContentServiceMap.get(bookIndex.getStorageType())
            .queryBookContent(bookId, lastBookIndexId).getContent();
        if (lastBookContent.length() > 42) {
            lastBookContent = lastBookContent.substring(0, 42);
        }
        data.put("lastBookContent", lastBookContent);
        return RestResult.ok(data);
    }

    private User currentUserProfile(HttpServletRequest request) {
        UserDetails userDetails = getUserDetails(request);
        return userDetails == null ? null : userService.userInfo(userDetails.getId());
    }

    /**
     * Truy vấn tác phẩm cùng loại theo ID danh mục
     */
    @GetMapping("listRecBookByCatId")
    public RestResult<List<Book>> listRecBookByCatId(Integer catId) {
        return RestResult.ok(bookService.listRecBookByCatId(catId));
    }


    /**
     * Truy vấn phân trang danh sách bình luận tác phẩm
     */
    @GetMapping("listCommentByPage")
    public RestResult<PageBean<BookCommentVO>> listCommentByPage(@RequestParam("bookId") Long bookId,
        @RequestParam(value = "curr", defaultValue = "1") int page,
        @RequestParam(value = "limit", defaultValue = "5") int pageSize) {
        return RestResult.ok(bookService.listCommentByPage(null, bookId, page, pageSize));
    }

    /**
     * Truy vấn phân trang danh sách phản hồi bình luận
     */
    @GetMapping("listCommentReplyByPage")
    public RestResult<PageBean<BookCommentReplyVO>> listCommentReplyByPage(@RequestParam("commentId") Long commentId,
        @RequestParam(value = "curr", defaultValue = "1") int page,
        @RequestParam(value = "limit", defaultValue = "5") int pageSize) {
        return RestResult.ok(bookService.listCommentReplyByPage(null, commentId, page, pageSize));
    }

    /**
     * Thêm đánh giá
     */
    @PostMapping("addBookComment")
    @com.java2nb.novel.common.annotation.RateLimit(key = "comment", count = 10, timeWindowSeconds = 60, limitType = com.java2nb.novel.common.annotation.LimitType.USER)
    @com.java2nb.novel.common.annotation.AuditLog(module = "CONTENT", eventType = "ADD_COMMENT", detail = "Nguoi dung viet binh luan/danh gia")
    public RestResult<?> addBookComment(BookComment comment, HttpServletRequest request) {
        UserDetails userDetails = getUserDetails(request);
        if (userDetails == null) {
            return RestResult.fail(ResponseStatus.NO_LOGIN);
        }
        comment.setLocation(ipLocationService.getLocation(IpUtil.getRealIp(request)));
        bookService.addBookComment(userDetails.getId(), comment);
        return RestResult.ok();
    }

    /**
     * Thích hoặc bỏ thích đánh giá
     */
    @PostMapping("toggleCommentLike")
    public RestResult<?> toggleCommentLike(Long commentId, HttpServletRequest request) {
        UserDetails userDetails = getUserDetails(request);
        if (userDetails == null) {
            return RestResult.fail(ResponseStatus.NO_LOGIN);
        }
        return RestResult.ok(likeService.toggleCommentLike(commentId, userDetails.getId()));
    }

    /**
     * Không thích hoặc bỏ không thích đánh giá
     */
    @PostMapping("toggleCommentUnLike")
    public RestResult<?> toggleCommentUnLike(Long commentId, HttpServletRequest request) {
        UserDetails userDetails = getUserDetails(request);
        if (userDetails == null) {
            return RestResult.fail(ResponseStatus.NO_LOGIN);
        }
        return RestResult.ok(likeService.toggleCommentUnLike(commentId, userDetails.getId()));
    }

    /**
     * Thêm phản hồi
        bookService.addBookCommentReply(userDetails.getId(), commentReply);
        return RestResult.ok();
    }

    /**
     * Thích hoặc bỏ thích phản hồi
     */
    @PostMapping("toggleReplyLike")
    public RestResult<?> toggleReplyLike(Long replyId, HttpServletRequest request) {
        UserDetails userDetails = getUserDetails(request);
        if (userDetails == null) {
            return RestResult.fail(ResponseStatus.NO_LOGIN);
        }
        return RestResult.ok(likeService.toggleReplyLike(replyId, userDetails.getId()));
    }

    /**
     * Thích hoặc bỏ thích phản hồi
     */
    @PostMapping("toggleReplyUnLike")
    public RestResult<?> toggleReplyUnLike(Long replyId, HttpServletRequest request) {
        UserDetails userDetails = getUserDetails(request);
        if (userDetails == null) {
            return RestResult.fail(ResponseStatus.NO_LOGIN);
        }
        return RestResult.ok(likeService.toggleReplyUnLike(replyId, userDetails.getId()));
    }

    /**
     * Truy vấn mười mục lục cập nhật mới nhất của tác phẩm theo ID tác phẩm
     */
    @GetMapping("queryNewIndexList")
    public RestResult<List<BookIndex>> queryNewIndexList(Long bookId) {
        return RestResult.ok(bookService.queryIndexList(bookId, "index_num desc", 1, 10));
    }

    /**
     * Trang mục lục
     */
    @GetMapping("/queryIndexList")
    public RestResult<PageBean<BookIndex>> indexList(Long bookId,
        @RequestParam(value = "curr", defaultValue = "1") int page,
        @RequestParam(value = "limit", defaultValue = "5") int pageSize,
        @RequestParam(value = "orderBy", defaultValue = "index_num desc") String orderBy) {
        return RestResult.ok(PageBuilder.build(bookService.queryIndexList(bookId, orderBy, page, pageSize)));
    }


}
