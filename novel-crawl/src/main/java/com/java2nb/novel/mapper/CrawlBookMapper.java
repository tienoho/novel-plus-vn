package com.java2nb.novel.mapper;

import com.java2nb.novel.entity.Book;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * @author Administrator
 */
public interface CrawlBookMapper extends BookMapper {

    /**
     * Truy vấn dữ liệu truyện cần cập nhật
     * @param startDate Thời điểm bắt đầu của khoảng cập nhật gần nhất
     * @param limit Số lượng bản ghi cần truy vấn
     * @return Danh sách truyện
     * */
    List<Book> queryNeedUpdateBook(@Param("startDate") Date startDate, @Param("limit") int limit);

    /**
     * Truy vấn tổng số chữ của truyện
     * @param bookId ID truyện
     * @return Tổng số chữ của truyện
     * */
    Integer queryTotalWordCount(@Param("bookId") Long bookId);

    /**
     * Cập nhật hàng loạt thời gian thu thập gần nhất của truyện
     * @param books Danh sách truyện cần cập nhật
     * @param currentDate Thời gian hiện tại
     * */
    void updateCrawlLastTime(@Param("books") List<Book> books,@Param("currentDate") Date currentDate);
}
