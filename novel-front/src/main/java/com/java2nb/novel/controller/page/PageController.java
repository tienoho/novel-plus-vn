package com.java2nb.novel.controller.page;

import com.java2nb.novel.controller.BaseController;
import com.java2nb.novel.core.bean.UserDetails;
import com.java2nb.novel.core.config.GamificationProperties;
import com.java2nb.novel.core.config.VnpayProperties;
import com.java2nb.novel.core.enums.ResponseStatus;
import com.java2nb.novel.core.exception.BusinessException;
import com.java2nb.novel.core.utils.AgeRatingUtil;
import com.java2nb.novel.core.utils.ThreadLocalUtil;
import com.java2nb.novel.entity.*;
import com.java2nb.novel.service.*;
import com.java2nb.novel.service.recommendation.RecommendationService;
import com.java2nb.novel.service.chapter.ChapterAccessDecision;
import com.java2nb.novel.service.chapter.ChapterCommercialPolicyService;
import com.java2nb.novel.service.entitlement.ReadingTicketService;
import com.java2nb.novel.vo.BookCommentVO;
import com.java2nb.novel.vo.BookSettingVO;
import io.github.xxyopen.model.page.PageBean;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author 11797
 */
@Slf4j
@RequiredArgsConstructor
@Controller
public class PageController extends BaseController {

    private final BookService bookService;

    private final NewsService newsService;

    private final AuthorService authorService;

    private final UserService userService;

    private final RecommendationService recommendationService;

    private final ChapterCommercialPolicyService chapterCommercialPolicyService;

    private final ReadingTicketService readingTicketService;

    private final ThreadPoolExecutor threadPoolExecutor;

    private final Map<String, BookContentService> bookContentServiceMap;

    private final VnpayProperties vnpayProperties;

    private final GamificationProperties gamificationProperties;

    @RequestMapping("{url}.html")
    public String module(@PathVariable("url") String url) {
        return url;
    }

    @RequestMapping("{module}/{url}.html")
    public String module2(@PathVariable("module") String module, @PathVariable("url") String url,
        HttpServletRequest request) {

        if (request.getRequestURI().startsWith("/author")) {
            //Truy cập khu vực tác giả
            UserDetails user = getUserDetails(request);
            if (user == null) {
                //Chưa đăng nhập
                return "redirect:/user/login.html?originUrl=" + request.getRequestURI();
            }

            boolean isAuthor = authorService.isAuthor(user.getId());
            if (!isAuthor) {
                return "redirect:/author/register.html";
            }
        }

        return module + "/" + url;
    }

    @RequestMapping("{module}/{classify}/{url}.html")
    public String module3(@PathVariable("module") String module, @PathVariable("classify") String classify,
        @PathVariable("url") String url) {
        return module + "/" + classify + "/" + url;
    }

    /**
     * Trang chủ
     */
    @SneakyThrows
    @RequestMapping(path = {"/", "/index", "/index.html"})
    public String index(Model model, HttpServletRequest request) {
        UserDetails userDetails = getUserDetails(request);
        Long userId = userDetails == null ? null : userDetails.getId();
        User userProfile = userId == null ? null : userService.userInfo(userId);
        //Luồng tải thông tin cơ bản tác phẩm trên trang chủ
        CompletableFuture<Map<String, List<BookSettingVO>>> bookCompletableFuture = CompletableFuture.supplyAsync(
            bookService::listBookSettingVO, threadPoolExecutor);
        CompletableFuture<List<BookSettingVO>> recommendationCompletableFuture = CompletableFuture.supplyAsync(
            () -> recommendationService.recommendHomeBooks(userId, userProfile, 6), threadPoolExecutor);
        //Luồng tải tin tức trang chủ
        CompletableFuture<List<News>> newsCompletableFuture = CompletableFuture.supplyAsync(newsService::listIndexNews,
            threadPoolExecutor);
        Map<String, List<BookSettingVO>> bookMap = new HashMap<>(bookCompletableFuture.get());
        bookMap.put("4", recommendationCompletableFuture.get());
        model.addAttribute("bookMap", bookMap);
        model.addAttribute("personalizedRecommendations", userId != null);
        model.addAttribute("newsList", newsCompletableFuture.get());
        return ThreadLocalUtil.getTemplateDir() + "index";
    }

    /**
     * Trang đăng nhập
     */
    @RequestMapping("user/login.html")
    public String login() {
        return ThreadLocalUtil.getTemplateDir() + "user/login";
    }

    /**
     * Trang đăng ký
     */
    @RequestMapping("user/register.html")
    public String register() {
        return ThreadLocalUtil.getTemplateDir() + "user/register";
    }

    /**
     * Trang trung tâm người dùng
     */
    @RequestMapping("user/userinfo.html")
    public String userinfo() {
        return ThreadLocalUtil.getTemplateDir() + "user/userinfo";
    }

    /**
     * Trang tủ sách của tôi
     */
    @RequestMapping("user/favorites.html")
    public String favorites() {
        return ThreadLocalUtil.getTemplateDir() + "user/favorites";
    }

    /** Trang hộp thông báo của độc giả. */
    @RequestMapping("user/notifications.html")
    public String notifications() {
        return ThreadLocalUtil.getTemplateDir() + "user/notifications";
    }

    /**
     * Trang lịch sử đọc
     */
    @RequestMapping("user/read_history.html")
    public String readHistory() {
        return ThreadLocalUtil.getTemplateDir() + "user/read_history";
    }

    /** Trang hồ sơ gamification và nhiệm vụ của độc giả. */
    @RequestMapping("user/quests.html")
    public String userQuests() {
        return ThreadLocalUtil.getTemplateDir() + "user/quests";
    }

    /** Trang đổi mã quà của độc giả. */
    @RequestMapping("user/gift_codes.html")
    public String userGiftCodes(HttpServletRequest request) {
        if (getUserDetails(request) == null) {
            return "redirect:/user/login.html?originUrl=/user/gift_codes.html";
        }
        return ThreadLocalUtil.getTemplateDir() + "user/gift_codes";
    }

    /** Trang số dư Vé đọc và quyền lợi thuê bao của độc giả. */
    @RequestMapping("user/reading_tickets.html")
    public String userReadingTickets(HttpServletRequest request) {
        if (getUserDetails(request) == null) {
            return "redirect:/user/login.html?originUrl=/user/reading_tickets.html";
        }
        return ThreadLocalUtil.getTemplateDir() + "user/reading_tickets";
    }

    /**
     * Trang nạp Xu
     */
    @RequestMapping("pay/index.html")
    public String pay(Model model) {
        model.addAttribute("vnpayEnabled", vnpayProperties.isConfigured());
        model.addAttribute("vnpayXuPerThousandVnd", vnpayProperties.getXuPerThousandVnd());
        model.addAttribute("vnpayAllowedAmountsVnd", vnpayProperties.getDisplayAmountsVnd());
        return ThreadLocalUtil.getTemplateDir() + "pay/index.html";
    }


    /**
     * Trang tác phẩm
     */
    @RequestMapping("book/bookclass.html")
    public String bookClass() {
        return "book/bookclass";
    }

    /**
     * Trang xếp hạng
     */
    @RequestMapping("book/book_ranking.html")
    public String bookRank() {

        return ThreadLocalUtil.getTemplateDir() + "book/book_ranking";
    }


    /**
     * Trang chi tiết
     */
    @SneakyThrows
    @RequestMapping("/book/{bookId}.html")
    public String bookDetail(@PathVariable("bookId") Long bookId, Model model, HttpServletRequest request) {
        UserDetails userDetails = getUserDetails(request);
        Long userId = userDetails == null ? null : userDetails.getId();
        User userProfile = userId == null ? null : userService.userInfo(userId);
        //Luồng tải thông tin cơ bản tác phẩm
        CompletableFuture<Book> bookCompletableFuture = CompletableFuture.supplyAsync(() -> {
            //Truy vấn tác phẩm
            Book book = bookService.queryBookDetail(bookId);
            log.debug("Đã tải xong thông tin cơ bản của tác phẩm");
            return book;
        }, threadPoolExecutor);
        //Luồng tải danh sách bình luận
        CompletableFuture<PageBean<BookCommentVO>> bookCommentPageBeanCompletableFuture = CompletableFuture.supplyAsync(
            () -> {
                PageBean<BookCommentVO> bookCommentVOPageBean = bookService.listCommentByPage(null, bookId, 1, 5);
                log.debug("Đã tải xong danh sách bình luận tác phẩm");
                return bookCommentVOPageBean;
            }, threadPoolExecutor);
        //Luồng tải chương đầu chạy sau khi tải xong thông tin tác phẩm
        CompletableFuture<Long> firstBookIndexIdCompletableFuture = bookCompletableFuture.thenApplyAsync((book) -> {
            if (book.getLastIndexId() != null) {
                //Truy vấn ID mục lục chương đầu
                Long firstBookIndexId = bookService.queryFirstBookIndexId(bookId);
                log.debug("Đã tải xong thông tin cơ bản của tác phẩm");
                return firstBookIndexId;
            }
            return null;
        }, threadPoolExecutor);
        //Luồng tải đề xuất an toàn chạy sau khi tải xong thông tin tác phẩm
        CompletableFuture<List<Book>> recBookCompletableFuture = bookCompletableFuture.thenApplyAsync((book) -> {
            List<Book> books = recommendationService.recommendBooks(userId, userProfile, book.getCatId(), bookId, 4);
            log.debug("Đã tải xong danh sách tác phẩm đề xuất");
            return books;
        }, threadPoolExecutor);

        Book book = bookCompletableFuture.get();
        requirePublicBookAccess(book, userProfile);
        model.addAttribute("book", book);
        model.addAttribute("firstBookIndexId", firstBookIndexIdCompletableFuture.get());
        model.addAttribute("recBooks", recBookCompletableFuture.get());
        model.addAttribute("bookCommentPageBean", bookCommentPageBeanCompletableFuture.get());

        return ThreadLocalUtil.getTemplateDir() + "book/book_detail";
    }

    /**
     * Trang mục lục
     */
    @SneakyThrows
    @RequestMapping("/book/indexList-{bookId}.html")
    public String indexList(@PathVariable("bookId") Long bookId, Model model, HttpServletRequest request) {
        Book book = bookService.queryBookDetail(bookId);
        requirePublicBookAccess(book, currentUserProfile(request));
        model.addAttribute("book", book);
        List<BookIndex> bookIndexList = bookService.queryIndexList(bookId, null, 1, null);
        model.addAttribute("bookIndexList", bookIndexList);
        model.addAttribute("bookIndexCount", bookIndexList.size());
        return ThreadLocalUtil.getTemplateDir() + "book/book_index";
    }

    /**
     * Trang nội dung
     */
    @SneakyThrows
    @RequestMapping("/book/{bookId}/{bookIndexId}.html")
    public String bookContent(@PathVariable("bookId") Long bookId, @PathVariable("bookIndexId") Long bookIndexId,
        HttpServletRequest request, HttpServletResponse response, Model model) {
        response.setHeader("Cache-Control", "private, no-store, max-age=0");
        response.setHeader("Pragma", "no-cache");
        response.addHeader("Vary", "Cookie");
        response.addHeader("Vary", "Authorization");
        //Luồng tải thông tin cơ bản tác phẩm
        CompletableFuture<Book> bookCompletableFuture = CompletableFuture.supplyAsync(() -> {
            //Truy vấn tác phẩm
            Book book = bookService.queryBookDetail(bookId);
            log.debug("Đã tải xong thông tin cơ bản của tác phẩm");
            return book;
        }, threadPoolExecutor);

        //Luồng tải thông tin chương
        CompletableFuture<BookIndex> bookIndexCompletableFuture = CompletableFuture.supplyAsync(() -> {
            //Truy vấn mục lục
            BookIndex bookIndex = bookService.queryBookIndex(bookIndexId);
            log.debug("Đã tải xong thông tin chương");
            return bookIndex;
        }, threadPoolExecutor);

        //Luồng tải chương trước chạy sau khi tải xong thông tin chương
        CompletableFuture<Long> preBookIndexIdCompletableFuture = bookIndexCompletableFuture.thenApplyAsync(
            (bookIndex) -> {
                //Truy vấn ID mục lục chương trước
                Long preBookIndexId = bookService.queryPreBookIndexId(bookId, bookIndex.getIndexNum());
                log.debug("Đã tải xong thông tin chương trước");
                return preBookIndexId;
            }, threadPoolExecutor);

        //Luồng tải chương tiếp theo chạy sau khi tải xong thông tin chương
        CompletableFuture<Long> nextBookIndexIdCompletableFuture = bookIndexCompletableFuture.thenApplyAsync(
            (bookIndex) -> {
                //Truy vấn ID mục lục chương tiếp theo
                Long nextBookIndexId = bookService.queryNextBookIndexId(bookId, bookIndex.getIndexNum());
                log.debug("Đã tải xong thông tin chương tiếp theo");
                return nextBookIndexId;
            }, threadPoolExecutor);

        //Luồng tải nội dung chạy sau khi tải xong thông tin chương
        CompletableFuture<BookContent> bookContentCompletableFuture = bookIndexCompletableFuture.thenApplyAsync(
            (bookIndex) -> {
                //Truy vấn nội dung
                BookContent bookContent = bookContentServiceMap.get(bookIndex.getStorageType())
                    .queryBookContent(bookId, bookIndexId);
                log.debug("Đã tải xong nội dung tác phẩm");
                return bookContent;
            }, threadPoolExecutor);

        //Luồng kiểm tra yêu cầu mua chương chạy sau khi tải xong thông tin chương
        CompletableFuture<ChapterAccessDecision> accessCompletableFuture = bookIndexCompletableFuture
            .thenApplyAsync(bookIndex -> {
                UserDetails user = getUserDetails(request);
                Date now = new Date();
                boolean purchased = user != null && userService.queryIsBuyBookIndex(user.getId(), bookIndexId);
                boolean entitled = user != null && !purchased
                    && readingTicketService.hasActiveChapterEntitlement(user.getId(), bookIndexId, now);
                ChapterAccessDecision decision = chapterCommercialPolicyService.evaluate(
                    bookIndex, purchased || entitled, now);
                log.debug("Đã kiểm tra xong quyền đọc chương của người dùng");
                return decision;
            }, threadPoolExecutor);

        Book book = bookCompletableFuture.get();
        requirePublicBookAccess(book, currentUserProfile(request));

        BookIndex bookIndex = bookIndexCompletableFuture.get();
        ResponseStatus chapterDenial = AgeRatingUtil.publicChapterDenialReason(bookIndex, bookId);
        if (chapterDenial != null) {
            throw new BusinessException(chapterDenial);
        }

        model.addAttribute("book", book);
        model.addAttribute("bookIndex", bookIndex);
        model.addAttribute("preBookIndexId", preBookIndexIdCompletableFuture.get());
        model.addAttribute("nextBookIndexId", nextBookIndexIdCompletableFuture.get());
        model.addAttribute("bookContent", bookContentCompletableFuture.get());
        ChapterAccessDecision access = accessCompletableFuture.get();
        model.addAttribute("needBuy", access.purchaseRequired());
        model.addAttribute("offlineEligible", access.offlineEligible());
        model.addAttribute("temporaryFree", access.temporaryFree());
        model.addAttribute("readingHeartbeatEnabled", gamificationProperties.isReadingHeartbeatEnabled());

        return ThreadLocalUtil.getTemplateDir() + "book/book_content";
    }

    /**
     * Trang bình luận
     */
    @RequestMapping("/book/comment-{bookId}.html")
    public String commentList(@PathVariable("bookId") Long bookId, Model model, HttpServletRequest request) {
        //Truy vấn tác phẩm
        Book book = bookService.queryBookDetail(bookId);
        requirePublicBookAccess(book, currentUserProfile(request));
        model.addAttribute("book", book);
        return "book/book_comment";
    }

    private User currentUserProfile(HttpServletRequest request) {
        UserDetails userDetails = getUserDetails(request);
        return userDetails == null ? null : userService.userInfo(userDetails.getId());
    }

    private void requirePublicBookAccess(Book book, User user) {
        ResponseStatus denialReason = AgeRatingUtil.publicBookDenialReason(book, user);
        if (denialReason != null) {
            throw new BusinessException(denialReason);
        }
    }

    /**
     * Trang phản hồi bình luận
     */
    @RequestMapping("/book/reply-{commentId}.html")
    public String commentReplyList(@PathVariable("commentId") Long commentId, Model model) {
        model.addAttribute("commentId", commentId);
        model.addAttribute("commentContent", bookService.getBookComment(commentId).getCommentContent());
        return "book/book_comment_reply";
    }

    /**
     * Trang nội dung tin tức
     */
    @RequestMapping("/about/newsInfo-{newsId}.html")
    public String newsInfo(@PathVariable("newsId") Long newsId, Model model) {
        //Truy vấn tin tức
        News news = newsService.queryNewsInfo(newsId);
        model.addAttribute("news", news);
        return "about/news_info";
    }


    /**
     * Trang lịch sử thưởng xếp hạng tháng của tác giả.
     */
    @RequestMapping("author/monthly_rewards.html")
    public String authorMonthlyRewards(HttpServletRequest request) {
        UserDetails user = getUserDetails(request);
        if (user == null) {
            return "redirect:/user/login.html?originUrl=/author/monthly_rewards.html";
        }
        if (!authorService.isAuthor(user.getId())) {
            return "redirect:/author/register.html";
        }
        return "author/monthly_rewards";
    }

    /**
     * Trang đăng ký tác giả
     */
    @RequestMapping("author/register.html")
    public String authorRegister(Author author, HttpServletRequest request, Model model) {
        UserDetails user = getUserDetails(request);
        if (user == null) {
            //Chưa đăng nhập
            return "redirect:/user/login.html?originUrl=/author/register.html";
        }

        if (StringUtils.isNotBlank(author.getInviteCode())) {
            //Gửi thông tin đăng ký tác giả
            String errorInfo = authorService.register(user.getId(), author);
            if (StringUtils.isBlank(errorInfo)) {
                //Đăng ký thành công
                return "redirect:/author/index.html";
            }
            model.addAttribute("LabErr", errorInfo);
            model.addAttribute("author", author);
        }
        return "author/register";
    }


}
