package com.java2nb.novel.service;


import com.java2nb.novel.entity.Author;
import com.java2nb.novel.entity.AuthorIncome;
import com.java2nb.novel.entity.AuthorIncomeDetail;
import io.github.xxyopen.model.page.PageBean;

import java.util.Date;
import java.util.List;

/**
 * @author 11797
 */
public interface AuthorService {

    /**
     * Kiểm tra bút danh có tồn tại hay không
     *
     * @param penName bút danh cần kiểm tra
     * @return true nếu bút danh tồn tại, false nếu không
     */
    Boolean checkPenName(String penName);

    /**
     * Đăng ký tác giả
     *
     * @param userId ID người dùng đăng ký
     * @param author thông tin đăng ký
     * @return thông báo lỗi
     */
    String register(Long userId, Author author);

    /**
     * Kiểm tra có phải tác giả hay không
     *
     * @param userId ID người dùng
     * @return true nếu là tác giả, false nếu không
     */
    Boolean isAuthor(Long userId);

    /**
     * Truy vấn thông tin tác giả
     *
     * @param userId ID người dùng
     * @return đối tượng tác giả
     */
    Author queryAuthor(Long userId);

    /**
     * Truy vấn danh sách tác giả
     *
     * @param limit số bản ghi cần truy vấn
     * @param maxAuthorCreateTime thời gian yêu cầu tối đa
     * @return danh sách tác giả
     */
    List<Author> queryAuthorList(int limit, Date maxAuthorCreateTime);

    /**
     * Kiểm tra thống kê thu nhập ngày đã được lưu hay chưa
     *
     * @param bookId ID tác phẩm
     * @param date thời gian thu nhập
     * @return true nếu đã lưu, false nếu chưa lưu
     */
    boolean queryIsStatisticsDaily(Long bookId, Date date);


    /**
     * Lưu thống kê thu nhập ngày theo tác phẩm
     *
     * @param authorIncomeDetail chi tiết thu nhập
     */
    void saveDailyIncomeSta(AuthorIncomeDetail authorIncomeDetail);


    /**
     * Truy vấn trạng thái lưu thống kê thu nhập tháng
     *
     * @param bookId ID tác phẩm
     * @param incomeDate thời gian thu nhập
     * @return true nếu đã lưu, false nếu chưa lưu
     */
    boolean queryIsStatisticsMonth(Long bookId, Date incomeDate);

    boolean queryIsStatisticsMonth(Long authorId, Long bookId, Date incomeDate);

    /**
     * Truy vấn tổng số Xu đăng ký trong khoảng thời gian
     *
     * @param userId
     * @param bookId ID tác phẩm
     * @param startTime thời gian bắt đầu
     * @param endTime thời gian kết thúc
     * @return số Xu đăng ký
     */
    Long queryTotalAccount(Long userId, Long bookId, Date startTime, Date endTime);


    /**
     * Lưu thống kê thu nhập tháng
     *
     * @param authorIncome chi tiết thu nhập
     */
    void saveAuthorIncomeSta(AuthorIncome authorIncome);

    /**
     * Kiểm tra thống kê thu nhập ngày đã được lưu hay chưa
     *
     * @param authorId ID tác giả
     * @param bookId ID tác phẩm
     * @param date thời gian thu nhập
     * @return true nếu đã lưu, false nếu chưa lưu
     */
    boolean queryIsStatisticsDaily(Long authorId, Long bookId, Date date);

    /**
     * Truy vấn phân trang thống kê thu nhập ngày của tác giả
     *
     * @param userId
     * @param page số trang
     * @param pageSize kích thước trang
     * @param bookId ID tác phẩm
     * @param startTime thời gian bắt đầu
     * @param endTime thời gian kết thúc
     * @return dữ liệu phân trang thống kê thu nhập ngày
     */
    PageBean<AuthorIncomeDetail> listIncomeDailyByPage(int page, int pageSize, Long userId, Long bookId, Date startTime,
        Date endTime);


    /**
     * Truy vấn phân trang thống kê thu nhập tháng của tác giả
     *
     * @param page số trang
     * @param pageSize kích thước trang
     * @param userId ID người dùng
     * @param bookId ID tác phẩm
     * @return dữ liệu phân trang
     */
    PageBean<AuthorIncome> listIncomeMonthByPage(int page, int pageSize, Long userId, Long bookId);
}
