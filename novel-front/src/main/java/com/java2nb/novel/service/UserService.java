package com.java2nb.novel.service;


import io.github.xxyopen.model.page.PageBean;
import com.java2nb.novel.core.bean.UserDetails;
import com.java2nb.novel.entity.UserBuyRecord;
import com.java2nb.novel.entity.UserFeedback;
import com.java2nb.novel.vo.BookReadHistoryVO;
import com.java2nb.novel.vo.BookShelfVO;
import com.java2nb.novel.entity.User;
import com.java2nb.novel.vo.UserFeedbackVO;

import java.util.Date;
import java.util.List;

/**
 * @author 11797
 */
public interface UserService {

    /**
     * Đăng ký người dùng
     * @param user thông tin đăng ký người dùng
     * @return thông tin JWT
     * */
    UserDetails register(User user);

    /**
     * Đăng nhập người dùng
     * @param user thông tin đăng nhập người dùng
     * @return thông tin JWT
     * */
    UserDetails login(User user);

    /**
     * Truy vấn trạng thái tác phẩm trong tủ sách
     * @param userId ID người dùng
     * @param bookId ID tác phẩm
     * @return true nếu đã thêm vào tủ sách, false nếu chưa
     * */
    Boolean queryIsInShelf(Long userId, Long bookId);

    /**
     * Thêm vào tủ sách
     * @param userId ID người dùng
     * @param bookId ID tác phẩm
     * @param preContentId ID nội dung đang đọc
     * */
    void addToBookShelf(Long userId, Long bookId, Long preContentId);

    /**
     * Xóa khỏi tủ sách
     * @param userId ID người dùng
     * @param bookId ID tác phẩm
     * */
    void removeFromBookShelf(Long userId, Long bookId);

    /**
     * Truy vấn tủ sách
     * @param userId ID người dùng
     * @param page
     * @param pageSize
     * @return thông tin phân trang tủ sách
     * */
    PageBean<BookShelfVO> listBookShelfByPage(Long userId, int page, int pageSize);

    /**
     * Thêm lịch sử đọc
     * @param userId ID người dùng
     * @param bookId ID tác phẩm
     * @param preContentId ID mục lục đang đọc
     * */
    void addReadHistory(Long userId, Long bookId, Long preContentId);

    /**
     * Thêm phản hồi
     * @param userId ID người dùng
     * @param content nội dung phản hồi
     * */
    void addFeedBack(Long userId, String content);

    /**
     * Truy vấn phân trang danh sách phản hồi của tôi
     * @param userId ID người dùng
     * @param page số trang
     * @param pageSize kích thước trang
     * @return dữ liệu phân trang phản hồi
     * */
    PageBean<UserFeedback> listUserFeedBackByPage(Long userId, int page, int pageSize);

    /**
     * Truy vấn thông tin cá nhân
     * @param userId ID người dùng
     * @return thông tin người dùng
     * */
    User userInfo(Long userId);

    /**
     * Truy vấn phân trang lịch sử đọc
     * @param userId ID người dùng
     * @param page số trang
     * @param pageSize kích thước trang
     * @return dữ liệu phân trang
     * */
    PageBean<BookReadHistoryVO> listReadHistoryByPage(Long userId, int page, int pageSize);

    /**
     * Cập nhật thông tin cá nhân
     * @param userId ID người dùng
     * @param user thông tin cần cập nhật
     * */
    void updateUserInfo(Long userId, User user);

    /**
     * Cập nhật mật khẩu
     * @param userId ID người dùng
     * @param oldPassword mật khẩu cũ
     * @param newPassword mật khẩu mới
     * */
    void updatePassword(Long userId, String oldPassword, String newPassword);


    /**
     * Tăng số dư người dùng
     * @param userId ID người dùng
     * @param amount số dư cần tăng */
    void addAmount(Long userId, int amount);

    /**
     * Kiểm tra người dùng đã mua chương hay chưa
     * @param userId ID người dùng
     * @param bookIndexId ID mục lục chương
     * @return true nếu đã mua, false nếu chưa
     * */
    boolean queryIsBuyBookIndex(Long userId, Long bookIndexId);

    /**
     * Mua chương tác phẩm
     * @param userId ID người dùng
     * @param buyRecord thông tin mua hàng
     * */
    void buyBookIndex(Long userId, UserBuyRecord buyRecord);

    /**
     * Truy vấn số người đăng ký tác phẩm trong khoảng thời gian
     * @param bookId ID tác phẩm
     * @param startTime thời gian bắt đầu
     * @param endTime thời gian kết thúc
     * @return số người đăng ký
     */
    int queryBuyMember(Long bookId, Date startTime, Date endTime);

    /**
     * Truy vấn số lượt đăng ký tác phẩm trong khoảng thời gian
     * @param bookId ID tác phẩm
     * @param startTime thời gian bắt đầu
     * @param endTime thời gian kết thúc
     * @return số lượt đăng ký
     */
    int queryBuyCount(Long bookId, Date startTime, Date endTime);

    /**
     * Truy vấn tổng Xu đăng ký tác phẩm trong khoảng thời gian
     * @param bookId ID tác phẩm
     * @param startTime thời gian bắt đầu
     * @param endTime thời gian kết thúc
     * @return tổng Xu đăng ký
     */
    int queryBuyAccount(Long bookId, Date startTime, Date endTime);

    /**
     * Truy vấn số người đăng ký của tác giả trong khoảng thời gian
     * @param bookIds toàn bộ ID tác phẩm của tác giả
     * @param startTime thời gian bắt đầu
     * @param endTime thời gian kết thúc
     * @return số người đăng ký
     */
    int queryBuyTotalMember(List<Long> bookIds, Date startTime, Date endTime);
}
