package com.java2nb.novel.service;


import com.java2nb.novel.vo.*;
import io.github.xxyopen.model.page.PageBean;
import com.java2nb.novel.entity.*;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * @author 11797
 */
public interface BookService {

    /**
     * Truy vấn dữ liệu cấu hình tác phẩm trang chủ
     *
     * @return
     */
    Map<String, List<BookSettingVO>> listBookSettingVO();

    /**
     * Truy vấn dữ liệu bảng lượt xem trang chủ
     * @return
     * */
    List<Book> listClickRank();

    /**
     * Truy vấn dữ liệu bảng tác phẩm mới trang chủ
     * @return danh sách tác phẩm
     * */
    List<Book> listNewRank();

    /**
     * Truy vấn dữ liệu bảng cập nhật trang chủ
     * @return
     * */
    List<BookVO> listUpdateRank();

    /**
     * Tìm kiếm phân trang
     * @param params tham số tìm kiếm
     * @param page số trang
     * @param pageSize kích thước trang
     * @return thông tin phân trang tác phẩm
     * */
    PageBean<?> searchByPage(BookSpVO params, int page, int pageSize);

    /**
     * Truy vấn danh sách danh mục tác phẩm
     * @return tập danh mục
     * */
    List<BookCategory> listBookCategory();

    /**
     * Truy vấn thông tin chi tiết tác phẩm
     * @return thông tin tác phẩm
     * @param id ID tác phẩm*/
    Book queryBookDetail(Long id);

    /**
     * Truy vấn danh sách mục lục
     * @param bookId ID tác phẩm
     * @param orderBy thứ tự sắp xếp
     * @param page số trang truy vấn
     *@param pageSize kích thước trang
     *@return tập mục lục
     * */
    List<BookIndex> queryIndexList(Long bookId, String orderBy, Integer page, Integer pageSize);


    /**
     * Truy vấn mục lục
     * @param bookIndexId ID mục lục
     * @return thông tin mục lục
     * */
    BookIndex queryBookIndex(Long bookIndexId);

    /**
     *Truy vấn ID mục lục chương trướcD
     * @param bookId ID tác phẩm
     * @param indexNum số mục lục
     * @return ID mục lục chương trước, trả 0 nếu không có
     * */
    Long queryPreBookIndexId(Long bookId, Integer indexNum);

    /**
     *Truy vấn ID mục lục chương tiếp theoD
     * @param bookId ID tác phẩm
     * @param indexNum số mục lục
     * @return ID mục lục chương tiếp theo, trả 0 nếu không có
     * */
    Long queryNextBookIndexId(Long bookId, Integer indexNum);

    /**
     * Truy vấn nội dung chương
     * @param bookIndexId ID mục lục
     * @return nội dung tác phẩm
     * */
    @Deprecated
    BookContent queryBookContent(Long bookIndexId);

    /**
     * Truy vấn thông tin xếp hạng tác phẩm
     * @param type loại xếp hạng: 0 lượt xem, 1 tác phẩm mới, 2 cập nhật
     * @param limit số bản ghi cần truy vấn
     * @return tập tác phẩm
     * */
    List<Book> listRank(Byte type, Integer limit);

    /**
     * Tăng lượt xem
     * @param bookId ID tác phẩm
     * @param visitCount lượt xem
     * */
    void addVisitCount(Long bookId, Integer visitCount);

    /**
     * Truy vấn số chương
     * @param bookId ID tác phẩm
     * @return số chương
     * */
    long queryIndexCount(Long bookId);

    /**
     * Truy vấn tác phẩm cùng loại theo ID danh mục
     * @param catId ID danh mục
     * @return tập tác phẩm
     * */
    List<Book> listRecBookByCatId(Integer catId);

    /**
     *Truy vấn ID mục lục chương đầuD
     * @param bookId ID tác phẩm
     * @return ID mục lục chương đầu
     * */
    Long queryFirstBookIndexId(Long bookId);

    /**
     *Truy vấn phân trang danh sách bình luận tác phẩm
     * @param userId ID người dùng
     * @param bookId ID tác phẩm
     * @param page số trang
     * @param pageSize kích thước trang
     * @return dữ liệu phân trang bình luận
     * */
    PageBean<BookCommentVO> listCommentByPage(Long userId, Long bookId, int page, int pageSize);

    /**
     * Thêm đánh giá
     * @param userId ID người dùng
     * @param comment nội dung bình luận
     * */
    void addBookComment(Long userId, BookComment comment);

    /**
     *Lấy hoặc tạo ID tác giả theo tênd
     * @param authorName tên tác giả
     * @param workDirection định hướng tác phẩm
     * @return ID tác giả
     * */
    @Deprecated
    Long getOrCreateAuthorIdByName(String authorName, Byte workDirection);



    /**
     *Truy vấn ID tác phẩmD
     * @param bookName tên tác phẩm
     * @param author tên tác giả
     * @return ID tác phẩm
     * */
    Long queryIdByNameAndAuthor(String bookName, String author);

    /**
     * Truy vấn tập số mục lục theo ID tác phẩm
     * @param bookId ID tác phẩm
     * @return tập số mục lục
     * */
    @Deprecated
    List<Integer> queryIndexNumByBookId(Long bookId);

    /**
     * Truy vấn tác phẩm có ảnh mạng
     *
     * @param localPicPrefix
     * @param limit số bản ghi cần truy vấn
     * @return tập tác phẩm
     * */
    List<Book> queryNetworkPicBooks(String localPicPrefix, Integer limit);


    /**
     * Cập nhật ảnh mạng của tác phẩm vào phương tiện lưu trữ của hệ thống (cục bộ, OSS, FastDFS)
     * @param picUrl đường dẫn ảnh mạng đã thu thập
     * @param bookId ID tác phẩm
     */
    void updateBookPicToLocal(String picUrl, Long bookId);

    /**
     * Truy vấn phân trang tác phẩm theo ID tác giả
     * @param userId ID người dùng
     * @param page số trang
     * @param pageSize kích thước trang
     * @return dữ liệu phân trang tác phẩm
     * */
    PageBean<Book> listBookPageByUserId(Long userId, int page, int pageSize);

    /**
     * Xuất bản tác phẩm
     * @param book thông tin tác phẩm
     * @param authorId ID tác giả
     * @param penName bút danh tác giả
     * */
    void addBook(Book book, Long authorId, String penName);

    /**
     * Cập nhật trạng thái phát hành hoặc gỡ tác phẩm
     * @param bookId ID tác phẩm
     * @param status trạng thái cần cập nhật
     * @param authorId ID tác giả
     * */
    void updateBookStatus(Long bookId, Byte status, Long authorId);

    /**
     * Xuất bản nội dung chương
     * @param bookId ID tác phẩm
     * @param indexName tên chương
     * @param content nội dung chương
     * @param isVip có thu phí hay không
     * @param authorId ID tác giả   */
    void addBookContent(Long bookId, String indexName, String content, Byte isVip, Long authorId);


    /**
     * Truy vấn phân trang tác phẩm theo thời gian cập nhật
     * @param startDate thời gian bắt đầu, bao gồm mốc này
     * @param limit số lượng cần truy vấn
     * @return danh sách tác phẩm
     * */
    List<Book> queryBookByUpdateTimeByPage(Date startDate, int limit);

    /**
     * Truy vấn danh sách tác phẩm
     * @param authorId ID tác giả
     * @return danh sách tác phẩm
     */
    List<Book> queryBookList(Long authorId);

    /**
     * Xóa chương
     * @param indexId
     * @param authorId ID tác giả
     */
    void deleteIndex(Long indexId, Long authorId);

    /**
     * Cập nhật tên chương
     * @param indexId
     * @param indexName
     * @param authorId
     */
    void updateIndexName(Long indexId, String indexName, Long authorId);

    /**
     * Truy vấn nội dung chương
     * @param indexId
     * @param authorId
     * @return
     */
    String queryIndexContent(Long indexId, Long authorId);

    /**
     *  Cập nhật nội dung chương
     * @param indexId
     * @param indexName
     * @param content
     * @param authorId
     */
    void updateBookContent( Long indexId, String indexName, String content, Long authorId);

    /**
     * Sửa bìa tác phẩm
     * @param bookId
     * @param bookPic
     * @param authorId
     */
    void updateBookPic(Long bookId, String bookPic, Long authorId);

    /**
     * Truy vấn ảnh do AI tạo
     */
    String queryAiGenPic(Long bookId);

    /**
     * Thêm phản hồi
     * @param userId ID người dùng
     * @param commentReply nội dung phản hồi
     * */
    void addBookCommentReply(Long userId, BookCommentReply commentReply);

    PageBean<BookCommentReplyVO> listCommentReplyByPage(Long userId, Long commentId, int page, int pageSize);

    BookComment getBookComment(Long commentId);
}
