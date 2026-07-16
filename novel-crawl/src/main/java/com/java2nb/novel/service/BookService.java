package com.java2nb.novel.service;

import com.java2nb.novel.entity.Book;
import com.java2nb.novel.entity.BookContent;
import com.java2nb.novel.entity.BookIndex;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * @author Administrator
 */
public interface BookService {


    /**
     * Kiểm tra tồn tại theo tên truyện và tác giả
     * @param bookName Tên truyện
     * @param authorName Tên tác giả
     * @return Truyện có cùng tên và tác giả có tồn tại hay không
     */
    boolean queryIsExistByBookNameAndAuthorName(String bookName, String authorName);

    /**
     * Cập nhật thuộc tính thu thập của truyện
     * @param id ID truyện trong hệ thống
     * @param sourceId ID nguồn thu thập
     * @param bookId ID truyện trên website nguồn  */
    void updateCrawlProperties(Long id, Integer sourceId, String bookId);

    /**
     * Truy vấn tên danh mục theo ID
     * @param catId ID danh mục
     * @return Tên danh mục
     * */
    String queryCatNameByCatId(int catId);

    /**
     * Lưu dữ liệu bảng truyện, mục lục và nội dung
     * @param book Dữ liệu truyện
     * @param bookIndexList Danh sách mục lục
     * @param bookContentList Danh sách nội dung
     * */
    void saveBookAndIndexAndContent(Book book, List<BookIndex> bookIndexList, List<BookContent> bookContentList);

    /**
     * Truy vấn dữ liệu truyện cần cập nhật
     *
     * @param startDate Thời điểm bắt đầu của khoảng cập nhật gần nhất
     * @param limit Số lượng bản ghi cần truy vấn
     * @return Danh sách truyện
     * */
    List<Book> queryNeedUpdateBook(Date startDate, int limit);

    /**
     * Truy vấn các chương đã tồn tại
     * @param bookId ID truyện
     * @return Map ánh xạ số chương sang dữ liệu chương
     * */
    Map<Integer,BookIndex> queryExistBookIndexMap(Long bookId);

    /**
     * Cập nhật dữ liệu bảng truyện, mục lục và nội dung
     * @param book Dữ liệu truyện
     * @param bookIndexList Danh sách mục lục
     * @param bookContentList Danh sách nội dung
     * @param existBookIndexMap  Map các chương đã tồn tại   */
    void updateBookAndIndexAndContent(Book book,  List<BookIndex> bookIndexList, List<BookContent> bookContentList, Map<Integer, BookIndex> existBookIndexMap);

    /**
     * Cập nhật thời gian thu thập gần nhất
     * @param bookId ID truyện
     * */
    @Deprecated
    void updateCrawlLastTime(Long bookId);

    /**
     * Truy vấn truyện đã tồn tại theo tên truyện và tác giả
     * @param bookName Tên truyện
     * @param authorName Tên tác giả
     * @return Đối tượng truyện
     * */
    Book queryBookByBookNameAndAuthorName(String bookName, String authorName);
}
