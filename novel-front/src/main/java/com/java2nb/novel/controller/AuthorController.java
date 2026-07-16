package com.java2nb.novel.controller;

import io.github.xxyopen.model.page.PageBean;

import com.java2nb.novel.core.bean.UserDetails;
import com.java2nb.novel.core.enums.ResponseStatus;
import io.github.xxyopen.model.resp.RestResult;
import io.github.xxyopen.web.exception.BusinessException;
import com.java2nb.novel.entity.Author;
import com.java2nb.novel.entity.AuthorIncome;
import com.java2nb.novel.entity.AuthorIncomeDetail;
import com.java2nb.novel.entity.Book;
import com.java2nb.novel.service.AuthorService;
import com.java2nb.novel.service.BookService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Date;

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

    private final ChatClient chatClient;

    private final OpenAiChatModel chatModel;

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

    /** Mở rộng nội dung bằng AI. */
    @PostMapping("ai/expand")
    public RestResult<String> expandText(@RequestParam("text") String text, @RequestParam("ratio") Double ratio) {
        String prompt = "Hãy mở rộng đoạn văn tiếng Việt sau lên khoảng " + ratio / 100
            + " lần độ dài ban đầu. Giữ nguyên ý, giọng văn và tên riêng; chỉ trả về nội dung đã viết lại: " + text;
        return RestResult.ok(chatClient.prompt()
            .user(prompt)
            .call()
            .content());
    }

    /** Rút gọn nội dung bằng AI. */
    @PostMapping("ai/condense")
    public RestResult<String> condenseText(@RequestParam("text") String text, @RequestParam("ratio") Integer ratio) {
        String prompt = "Hãy rút gọn đoạn văn tiếng Việt sau còn khoảng 1/" + 100 / ratio
            + " độ dài ban đầu. Giữ nguyên ý chính và tên riêng; chỉ trả về nội dung đã viết lại: " + text;
        return RestResult.ok(chatClient.prompt()
            .user(prompt)
            .call()
            .content());
    }

    /** Viết tiếp nội dung bằng AI. */
    @PostMapping("ai/continue")
    public RestResult<String> continueText(@RequestParam("text") String text, @RequestParam("length") Integer length) {
        String prompt = "Hãy viết tiếp đoạn văn tiếng Việt sau với độ dài khoảng " + length
            + " ký tự. Giữ nhất quán nhân vật, ngôi kể và giọng văn; chỉ trả về phần viết tiếp: " + text;
        return RestResult.ok(chatClient.prompt()
            .user(prompt)
            .call()
            .content());
    }

    /** Trau chuốt nội dung bằng AI. */
    @PostMapping("ai/polish")
    public RestResult<String> polishText(@RequestParam("text") String text) {
        String prompt = "Hãy trau chuốt đoạn văn tiếng Việt sau cho tự nhiên, mạch lạc; giữ nguyên ý và tên riêng, "
            + "chỉ trả về nội dung đã chỉnh sửa: " + text;
        return RestResult.ok(chatClient.prompt()
            .user(prompt)
            .call()
            .content());
    }

    /**
     * Mở rộng nội dung bằng AI theo luồng.
     */
    @GetMapping(value = "ai/stream/expand", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamExpandText(@RequestParam("text") String text, @RequestParam("ratio") Double ratio) {
        String prompt = "Hãy mở rộng đoạn văn tiếng Việt sau lên khoảng " + ratio / 100
            + " lần độ dài ban đầu. Giữ nguyên ý, giọng văn và tên riêng; chỉ trả về nội dung đã viết lại: " + text;
        return chatClient.prompt()
            .user(prompt)
            .stream()
            .content();
    }

    /**
     * Rút gọn nội dung bằng AI theo luồng.
     */
    @GetMapping(value = "ai/stream/condense", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamCondenseText(@RequestParam("text") String text, @RequestParam("ratio") Integer ratio) {
        String prompt = "Hãy rút gọn đoạn văn tiếng Việt sau còn khoảng 1/" + 100 / ratio
            + " độ dài ban đầu. Giữ nguyên ý chính và tên riêng; chỉ trả về nội dung đã viết lại: " + text;
        return chatClient.prompt()
            .user(prompt)
            .stream()
            .content();
    }

    /**
     * Viết tiếp nội dung bằng AI theo luồng.
     */
    @GetMapping(value = "ai/stream/continue", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamContinueText(@RequestParam("text") String text, @RequestParam("length") Integer length) {
        String prompt = "Hãy viết tiếp đoạn văn tiếng Việt sau với độ dài khoảng " + length
            + " ký tự. Giữ nhất quán nhân vật, ngôi kể và giọng văn; chỉ trả về phần viết tiếp: " + text;
        return chatClient.prompt()
            .user(prompt)
            .stream()
            .content();
    }

    /**
     * Trau chuốt nội dung bằng AI theo luồng.
     */
    @GetMapping(value = "/ai/stream/polish", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamPolishText(@RequestParam("text") String text) {
        String prompt = "Hãy trau chuốt đoạn văn tiếng Việt sau cho tự nhiên, mạch lạc; giữ nguyên ý và tên riêng, "
            + "chỉ trả về nội dung đã chỉnh sửa: " + text;
        return chatClient.prompt()
            .user(prompt)
            .stream()
            .content();
    }

}
