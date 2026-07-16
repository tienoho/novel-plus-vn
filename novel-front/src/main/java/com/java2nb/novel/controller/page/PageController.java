package com.java2nb.novel.controller.page;

import com.java2nb.novel.controller.BaseController;
import com.java2nb.novel.core.bean.UserDetails;
import com.java2nb.novel.core.config.VnpayProperties;
import com.java2nb.novel.core.utils.ThreadLocalUtil;
import com.java2nb.novel.entity.*;
import com.java2nb.novel.service.*;
import com.java2nb.novel.vo.BookCommentVO;
import com.java2nb.novel.vo.BookSettingVO;
import io.github.xxyopen.model.page.PageBean;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.Map;
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

    private final ThreadPoolExecutor threadPoolExecutor;

    private final Map<String, BookContentService> bookContentServiceMap;

    private final VnpayProperties vnpayProperties;

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
    public String index(Model model) {
        //Luồng tải thông tin cơ bản tác phẩm trên trang chủ
        CompletableFuture<Map<String, List<BookSettingVO>>> bookCompletableFuture = CompletableFuture.supplyAsync(
            bookService::listBookSettingVO, threadPoolExecutor);
        //Luồng tải tin tức trang chủ
        CompletableFuture<List<News>> newsCompletableFuture = CompletableFuture.supplyAsync(newsService::listIndexNews,
            threadPoolExecutor);
        model.addAttribute("bookMap", bookCompletableFuture.get());
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

    /**
     * Trang lịch sử đọc
     */
    @RequestMapping("user/read_history.html")
    public String readHistory() {
        return ThreadLocalUtil.getTemplateDir() + "user/read_history";
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
    public String bookDetail(@PathVariable("bookId") Long bookId, Model model) {
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
        //Luồng tải đề xuất ngẫu nhiên chạy sau khi tải xong thông tin tác phẩm
        CompletableFuture<List<Book>> recBookCompletableFuture = bookCompletableFuture.thenApplyAsync((book) -> {
            List<Book> books = bookService.listRecBookByCatId(book.getCatId());
            log.debug("Đã tải xong danh sách tác phẩm đề xuất ngẫu nhiên");
            return books;
        }, threadPoolExecutor);

        model.addAttribute("book", bookCompletableFuture.get());
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
    public String indexList(@PathVariable("bookId") Long bookId, Model model) {
        Book book = bookService.queryBookDetail(bookId);
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
        HttpServletRequest request, Model model) {
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
        CompletableFuture<Boolean> needBuyCompletableFuture = bookIndexCompletableFuture.thenApplyAsync((bookIndex) -> {
            //Kiểm tra mục lục có thu phí hay không
            if (bookIndex.getIsVip() != null && bookIndex.getIsVip() == 1) {
                //Có thu phí
                UserDetails user = getUserDetails(request);
                if (user == null) {
                    //Chưa đăng nhập và cần mua chương
                    return true;
                }
                //Kiểm tra người dùng đã mua mục lục hay chưa
                boolean isBuy = userService.queryIsBuyBookIndex(user.getId(), bookIndexId);
                if (!isBuy) {
                    //Chưa mua nên cần thanh toán
                    return true;
                }
            }

            log.debug("Đã kiểm tra xong yêu cầu mua chương của người dùng");
            return false;

        }, threadPoolExecutor);

        model.addAttribute("book", bookCompletableFuture.get());
        model.addAttribute("bookIndex", bookIndexCompletableFuture.get());
        model.addAttribute("preBookIndexId", preBookIndexIdCompletableFuture.get());
        model.addAttribute("nextBookIndexId", nextBookIndexIdCompletableFuture.get());
        model.addAttribute("bookContent", bookContentCompletableFuture.get());
        model.addAttribute("needBuy", needBuyCompletableFuture.get());

        return ThreadLocalUtil.getTemplateDir() + "book/book_content";
    }

    /**
     * Trang bình luận
     */
    @RequestMapping("/book/comment-{bookId}.html")
    public String commentList(@PathVariable("bookId") Long bookId, Model model) {
        //Truy vấn tác phẩm
        Book book = bookService.queryBookDetail(bookId);
        model.addAttribute("book", book);
        return "book/book_comment";
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
