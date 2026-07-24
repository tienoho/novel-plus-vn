package com.java2nb.novel.controller;


import com.java2nb.novel.core.bean.UserDetails;
import com.java2nb.novel.core.cache.CacheService;
import com.java2nb.novel.core.enums.ResponseStatus;
import com.java2nb.novel.core.utils.IpUtil;
import com.java2nb.novel.core.utils.RandomValidateCodeUtil;
import com.java2nb.novel.entity.Book;
import com.java2nb.novel.entity.BookIndex;
import com.java2nb.novel.entity.User;
import com.java2nb.novel.entity.UserBuyRecord;
import com.java2nb.novel.service.BookService;
import com.java2nb.novel.service.UserService;
import com.java2nb.novel.service.wallet.WalletLedgerService;
import io.github.xxyopen.model.resp.RestResult;
import io.github.xxyopen.web.valid.AddGroup;
import io.github.xxyopen.web.valid.UpdateGroup;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * @author 11797
 */
@RestController
@RequestMapping("user")
@RequiredArgsConstructor
@Slf4j
public class UserController extends BaseController {


    private final CacheService cacheService;

    private final UserService userService;

    private final BookService bookService;

    private final WalletLedgerService walletLedgerService;

    /**
     * Đăng nhập
     */
    @PostMapping("login")
    public RestResult<Map<String, Object>> login(User user) {

        //Đăng nhập
        UserDetails userDetails = userService.login(user);

        Map<String, Object> data = new HashMap<>(1);
        data.put("token", jwtTokenUtil.generateToken(userDetails));

        return RestResult.ok(data);


    }

    /**
     * Đăng ký
     */
    @PostMapping("register")
    public RestResult<?> register(@Validated({AddGroup.class}) User user,
        @RequestParam(value = "velCode", defaultValue = "") String velCode, HttpServletRequest request) {

        //Kiểm tra mã xác minh có đúng hay không
        if (!velCode.equals(
            cacheService.get(RandomValidateCodeUtil.RANDOM_CODE_KEY + ":" + IpUtil.getRealIp(request)))) {
            return RestResult.fail(ResponseStatus.VEL_CODE_ERROR);
        }

        //Đăng ký
        UserDetails userDetails = userService.register(user);
        Map<String, Object> data = new HashMap<>(1);
        data.put("token", jwtTokenUtil.generateToken(userDetails));

        return RestResult.ok(data);


    }


    /**
     *Làm mới tokenn
     */
    @PostMapping("refreshToken")
    public RestResult<?> refreshToken(HttpServletRequest request) {
        String token = getToken(request);
        if (jwtTokenUtil.canRefresh(token)) {
            token = jwtTokenUtil.refreshToken(token);
            Map<String, Object> data = new HashMap<>(2);
            data.put("token", token);
            UserDetails userDetail = jwtTokenUtil.getUserDetailsFromToken(token);
            data.put("username", userDetail.getUsername());
            data.put("nickName", userDetail.getNickName());
            return RestResult.ok(data);

        } else {
            return RestResult.fail(ResponseStatus.NO_LOGIN);
        }

    }

    /**
     * Truy vấn trạng thái tác phẩm trong tủ sách
     */
    @GetMapping("queryIsInShelf")
    public RestResult<?> queryIsInShelf(Long bookId, HttpServletRequest request) {
        UserDetails userDetails = getUserDetails(request);
        if (userDetails == null) {
            return RestResult.fail(ResponseStatus.NO_LOGIN);
        }
        return RestResult.ok(userService.queryIsInShelf(userDetails.getId(), bookId));
    }

    /**
     * Thêm vào tủ sách
     */
    @PostMapping("addToBookShelf")
    public RestResult<Void> addToBookShelf(Long bookId, Long preContentId, HttpServletRequest request) {
        UserDetails userDetails = getUserDetails(request);
        if (userDetails == null) {
            return RestResult.fail(ResponseStatus.NO_LOGIN);
        }
        userService.addToBookShelf(userDetails.getId(), bookId, preContentId);
        return RestResult.ok();
    }

    /**
     * Xóa khỏi tủ sách
     */
    @DeleteMapping("removeFromBookShelf/{bookId}")
    public RestResult<?> removeFromBookShelf(@PathVariable("bookId") Long bookId, HttpServletRequest request) {
        UserDetails userDetails = getUserDetails(request);
        if (userDetails == null) {
            return RestResult.fail(ResponseStatus.NO_LOGIN);
        }
        userService.removeFromBookShelf(userDetails.getId(), bookId);
        return RestResult.ok();
    }

    /**
     * Truy vấn phân trang tủ sách
     */
    @GetMapping("listBookShelfByPage")
    public RestResult<?> listBookShelfByPage(@RequestParam(value = "curr", defaultValue = "1") int page,
        @RequestParam(value = "limit", defaultValue = "10") int pageSize, HttpServletRequest request) {
        UserDetails userDetails = getUserDetails(request);
        if (userDetails == null) {
            return RestResult.fail(ResponseStatus.NO_LOGIN);
        }
        return RestResult.ok(userService.listBookShelfByPage(userDetails.getId(), page, pageSize));
    }

    /**
     * Truy vấn phân trang lịch sử đọc
     */
    @GetMapping("listReadHistoryByPage")
    public RestResult<?> listReadHistoryByPage(@RequestParam(value = "curr", defaultValue = "1") int page,
        @RequestParam(value = "limit", defaultValue = "10") int pageSize, HttpServletRequest request) {
        UserDetails userDetails = getUserDetails(request);
        if (userDetails == null) {
            return RestResult.fail(ResponseStatus.NO_LOGIN);
        }
        return RestResult.ok(userService.listReadHistoryByPage(userDetails.getId(), page, pageSize));
    }

    /**
     * Thêm lịch sử đọc
     */
    @PostMapping("addReadHistory")
    public RestResult<?> addReadHistory(Long bookId, Long preContentId, HttpServletRequest request) {
        UserDetails userDetails = getUserDetails(request);
        if (userDetails == null) {
            return RestResult.fail(ResponseStatus.NO_LOGIN);
        }
        userService.addReadHistory(userDetails.getId(), bookId, preContentId);
        return RestResult.ok();
    }

    /**
     * Thêm phản hồi
     */
    @PostMapping("addFeedBack")
    public RestResult<?> addFeedBack(String content, HttpServletRequest request) {
        UserDetails userDetails = getUserDetails(request);
        if (userDetails == null) {
            return RestResult.fail(ResponseStatus.NO_LOGIN);
        }
        userService.addFeedBack(userDetails.getId(), content);
        return RestResult.ok();
    }

    /**
     * Truy vấn phân trang danh sách phản hồi của tôi
     */
    @GetMapping("listUserFeedBackByPage")
    public RestResult<?> listUserFeedBackByPage(@RequestParam(value = "curr", defaultValue = "1") int page,
        @RequestParam(value = "limit", defaultValue = "5") int pageSize, HttpServletRequest request) {
        UserDetails userDetails = getUserDetails(request);
        if (userDetails == null) {
            return RestResult.fail(ResponseStatus.NO_LOGIN);
        }
        return RestResult.ok(userService.listUserFeedBackByPage(userDetails.getId(), page, pageSize));
    }

    /**
     * Truy vấn thông tin cá nhân
     */
    @GetMapping("userInfo")
    public RestResult<?> userInfo(HttpServletRequest request) {
        UserDetails userDetails = getUserDetails(request);
        if (userDetails == null) {
            return RestResult.fail(ResponseStatus.NO_LOGIN);
        }
        return RestResult.ok(userService.userInfo(userDetails.getId()));
    }

    /**
     * Cập nhật thông tin cá nhân
     */
    @PostMapping("updateUserInfo")
    public RestResult<?> updateUserInfo(@Validated({UpdateGroup.class}) User user, HttpServletRequest request) {
        UserDetails userDetails = getUserDetails(request);
        if (userDetails == null) {
            return RestResult.fail(ResponseStatus.NO_LOGIN);
        }
        userService.updateUserInfo(userDetails.getId(), user);
        if (user.getNickName() != null) {
            userDetails.setNickName(user.getNickName());
            Map<String, Object> data = new HashMap<>(1);
            data.put("token", jwtTokenUtil.generateToken(userDetails));
            return RestResult.ok(data);
        }
        return RestResult.ok();
    }


    /**
     * Cập nhật mật khẩu
     */
    @PostMapping("updatePassword")
    public RestResult<?> updatePassword(String oldPassword, String newPassword1, String newPassword2,
        HttpServletRequest request) {
        UserDetails userDetails = getUserDetails(request);
        if (userDetails == null) {
            return RestResult.fail(ResponseStatus.NO_LOGIN);
        }
        if (!(StringUtils.isNotBlank(newPassword1) && newPassword1.equals(newPassword2))) {
            RestResult.fail(ResponseStatus.TWO_PASSWORD_DIFF);
        }
        userService.updatePassword(userDetails.getId(), oldPassword, newPassword1);
        return RestResult.ok();
    }

    /**
     * Truy vấn phân trang đánh giá tác phẩm của người dùng
     */
    @GetMapping("listCommentByPage")
    public RestResult<?> listCommentByPage(@RequestParam(value = "curr", defaultValue = "1") int page,
        @RequestParam(value = "limit", defaultValue = "5") int pageSize, HttpServletRequest request) {
        UserDetails userDetails = getUserDetails(request);
        if (userDetails == null) {
            return RestResult.fail(ResponseStatus.NO_LOGIN);
        }
        return RestResult.ok(bookService.listCommentByPage(userDetails.getId(), null, page, pageSize));
    }


    /**
     * Mua chương tác phẩm
     */
    @PostMapping("buyBookIndex")
    public RestResult<?> buyBookIndex(UserBuyRecord buyRecord, HttpServletRequest request) {
        UserDetails userDetails = getUserDetails(request);
        if (userDetails == null) {
            return RestResult.fail(ResponseStatus.NO_LOGIN);
        }
        BookIndex bookIndex = bookService.queryBookIndex(buyRecord.getBookIndexId());
        Book book = bookService.queryBookDetail(bookIndex.getBookId());
        UserBuyRecord authoritativeRecord = new UserBuyRecord();
        authoritativeRecord.setBookIndexId(bookIndex.getId());
        authoritativeRecord.setBookIndexName(bookIndex.getIndexName());
        authoritativeRecord.setBookId(book.getId());
        authoritativeRecord.setBookName(book.getBookName());
        authoritativeRecord.setBuyAmount(bookIndex.getBookPrice());
        userService.buyBookIndex(userDetails.getId(), book.getAuthorId(), authoritativeRecord);
        return RestResult.ok();
    }

    /**
     * Lịch sử biến động ví Xu của độc giả hiện tại.
     */
    @GetMapping("wallet/transactions")
    public RestResult<?> listWalletTransactions(@RequestParam(value = "page", defaultValue = "1") int page,
                                                @RequestParam(value = "limit", defaultValue = "20") int pageSize,
                                                HttpServletRequest request) {
        UserDetails userDetails = getUserDetails(request);
        if (userDetails == null) {
            return RestResult.fail(ResponseStatus.NO_LOGIN);
        }
        return RestResult.ok(walletLedgerService.listReaderHistory(userDetails.getId(), page, pageSize));
    }


}
