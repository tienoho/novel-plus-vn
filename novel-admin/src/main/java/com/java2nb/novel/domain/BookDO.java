package com.java2nb.novel.domain;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.java2nb.common.jsonserializer.LongToStringSerializer;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;


/**
 * Bảng tác phẩm
 *
 * @author xiongxy
 * @email 1179705413@qq.com
 * @date 2023-04-14 17:42:55
 */
public class BookDO implements Serializable {

    private static final long serialVersionUID = 1L;


    // Khóa chính
    // Kiểu long của Java có phạm vi lớn hơn number của JavaScript, nên một số giá trị không thể biểu diễn chính xác trong JavaScript
    // Vì vậy, giá trị được tuần tự hóa thành chuỗi để xử lý
    @JsonSerialize(using = LongToStringSerializer.class)
    private Long id;
    //Định hướng tác phẩm: 0 nam, 1 nữ
    private Integer workDirection;
    // ID danh mục
    private Integer catId;
    // Tên danh mục
    private String catName;
    // Bìa tác phẩm
    private String picUrl;
    // Tên tác phẩm
    private String bookName;
    //ID tác giả
    // Kiểu long của Java có phạm vi lớn hơn number của JavaScript, nên một số giá trị không thể biểu diễn chính xác trong JavaScript
    // Vì vậy, giá trị được tuần tự hóa thành chuỗi để xử lý
    @JsonSerialize(using = LongToStringSerializer.class)
    private Long authorId;
    //Tên tác giả
    private String authorName;
    //Mô tả tác phẩm
    private String bookDesc;
    // Điểm đánh giá, trường dự phòng
    private Float score;
    //Trạng thái tác phẩm: 0 đang ra, 1 hoàn thành
    private Integer bookStatus;
    // Lượt xem
    // Kiểu long của Java có phạm vi lớn hơn number của JavaScript, nên một số giá trị không thể biểu diễn chính xác trong JavaScript
    // Vì vậy, giá trị được tuần tự hóa thành chuỗi để xử lý
    @JsonSerialize(using = LongToStringSerializer.class)
    private Long visitCount;
    // Tổng số chữ
    private Integer wordCount;
    // Số bình luận
    private Integer commentCount;
    // Số lượt đăng ký hôm qua
    private Integer yesterdayBuy;
    // ID mục lục mới nhất
    // Kiểu long của Java có phạm vi lớn hơn number của JavaScript, nên một số giá trị không thể biểu diễn chính xác trong JavaScript
    // Vì vậy, giá trị được tuần tự hóa thành chuỗi để xử lý
    @JsonSerialize(using = LongToStringSerializer.class)
    private Long lastIndexId;
    // Tên mục lục mới nhất
    private String lastIndexName;
    // Thời gian cập nhật mục lục mới nhất
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastIndexUpdateTime;
    // Thu phí: 1 có, 0 miễn phí
    private Integer isVip;
    // Trạng thái: 0 nhập kho, 1 phát hành
    private Integer status;
    // Thời gian cập nhật
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
    // Thời gian tạo
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
    // ID nguồn thu thập
    private Integer crawlSourceId;
    // ID tác phẩm tại nguồn thu thập
    private String crawlBookId;
    // Thời gian thu thập gần nhất
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date crawlLastTime;
    // Đã dừng cập nhật: 0 chưa, 1 đã dừng
    private Integer crawlIsStop;

    /**
     * Đặt: khóa chính
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Lấy: khóa chính
     */
    public Long getId() {
        return id;
    }

    /**
     * Đặt: định hướng tác phẩm: 0 nam, 1 nữ
     */
    public void setWorkDirection(Integer workDirection) {
        this.workDirection = workDirection;
    }

    /**
     * Lấy: định hướng tác phẩm: 0 nam, 1 nữ
     */
    public Integer getWorkDirection() {
        return workDirection;
    }

    /**
     * Đặt: ID danh mục
     */
    public void setCatId(Integer catId) {
        this.catId = catId;
    }

    /**
     * Lấy: ID danh mục
     */
    public Integer getCatId() {
        return catId;
    }

    /**
     * Đặt: tên danh mục
     */
    public void setCatName(String catName) {
        this.catName = catName;
    }

    /**
     * Lấy: tên danh mục
     */
    public String getCatName() {
        return catName;
    }

    /**
     * Đặt: bìa tác phẩm
     */
    public void setPicUrl(String picUrl) {
        this.picUrl = picUrl;
    }

    /**
     * Lấy: bìa tác phẩm
     */
    public String getPicUrl() {
        return picUrl;
    }

    /**
     * Đặt: tên tác phẩm
     */
    public void setBookName(String bookName) {
        this.bookName = bookName;
    }

    /**
     * Lấy: tên tác phẩm
     */
    public String getBookName() {
        return bookName;
    }

    /**
     * Đặt: ID tác giả
     */
    public void setAuthorId(Long authorId) {
        this.authorId = authorId;
    }

    /**
     * Lấy: ID tác giả
     */
    public Long getAuthorId() {
        return authorId;
    }

    /**
     * Đặt: tên tác giả
     */
    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    /**
     * Lấy: tên tác giả
     */
    public String getAuthorName() {
        return authorName;
    }

    /**
     * Đặt: mô tả tác phẩm
     */
    public void setBookDesc(String bookDesc) {
        this.bookDesc = bookDesc;
    }

    /**
     * Lấy: mô tả tác phẩm
     */
    public String getBookDesc() {
        return bookDesc;
    }

    /**
     * Đặt: điểm đánh giá, trường dự phòng
     */
    public void setScore(Float score) {
        this.score = score;
    }

    /**
     * Lấy: điểm đánh giá, trường dự phòng
     */
    public Float getScore() {
        return score;
    }

    /**
     * Đặt: trạng thái tác phẩm: 0 đang ra, 1 hoàn thành
     */
    public void setBookStatus(Integer bookStatus) {
        this.bookStatus = bookStatus;
    }

    /**
     * Lấy: trạng thái tác phẩm: 0 đang ra, 1 hoàn thành
     */
    public Integer getBookStatus() {
        return bookStatus;
    }

    /**
     * Đặt: lượt xem
     */
    public void setVisitCount(Long visitCount) {
        this.visitCount = visitCount;
    }

    /**
     * Lấy: lượt xem
     */
    public Long getVisitCount() {
        return visitCount;
    }

    /**
     * Đặt: tổng số chữ
     */
    public void setWordCount(Integer wordCount) {
        this.wordCount = wordCount;
    }

    /**
     * Lấy: tổng số chữ
     */
    public Integer getWordCount() {
        return wordCount;
    }

    /**
     * Đặt: số bình luận
     */
    public void setCommentCount(Integer commentCount) {
        this.commentCount = commentCount;
    }

    /**
     * Lấy: số bình luận
     */
    public Integer getCommentCount() {
        return commentCount;
    }

    /**
     * Đặt: số lượt đăng ký hôm qua
     */
    public void setYesterdayBuy(Integer yesterdayBuy) {
        this.yesterdayBuy = yesterdayBuy;
    }

    /**
     * Lấy: số lượt đăng ký hôm qua
     */
    public Integer getYesterdayBuy() {
        return yesterdayBuy;
    }

    /**
     * Đặt: ID mục lục mới nhất
     */
    public void setLastIndexId(Long lastIndexId) {
        this.lastIndexId = lastIndexId;
    }

    /**
     * Lấy: ID mục lục mới nhất
     */
    public Long getLastIndexId() {
        return lastIndexId;
    }

    /**
     * Đặt: tên mục lục mới nhất
     */
    public void setLastIndexName(String lastIndexName) {
        this.lastIndexName = lastIndexName;
    }

    /**
     * Lấy: tên mục lục mới nhất
     */
    public String getLastIndexName() {
        return lastIndexName;
    }

    /**
     * Đặt: thời gian cập nhật mục lục mới nhất
     */
    public void setLastIndexUpdateTime(Date lastIndexUpdateTime) {
        this.lastIndexUpdateTime = lastIndexUpdateTime;
    }

    /**
     * Lấy: thời gian cập nhật mục lục mới nhất
     */
    public Date getLastIndexUpdateTime() {
        return lastIndexUpdateTime;
    }

    /**
     * Đặt: thu phí: 1 có, 0 miễn phí
     */
    public void setIsVip(Integer isVip) {
        this.isVip = isVip;
    }

    /**
     * Lấy: thu phí: 1 có, 0 miễn phí
     */
    public Integer getIsVip() {
        return isVip;
    }

    /**
     * Đặt: trạng thái: 0 nhập kho, 1 phát hành
     */
    public void setStatus(Integer status) {
        this.status = status;
    }

    /**
     * Lấy: trạng thái: 0 nhập kho, 1 phát hành
     */
    public Integer getStatus() {
        return status;
    }

    /**
     * Đặt: thời gian cập nhật
     */
    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    /**
     * Lấy: thời gian cập nhật
     */
    public Date getUpdateTime() {
        return updateTime;
    }

    /**
     * Đặt: thời gian tạo
     */
    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    /**
     * Lấy: thời gian tạo
     */
    public Date getCreateTime() {
        return createTime;
    }

    /**
     * Đặt: ID nguồn thu thập
     */
    public void setCrawlSourceId(Integer crawlSourceId) {
        this.crawlSourceId = crawlSourceId;
    }

    /**
     * Lấy: ID nguồn thu thập
     */
    public Integer getCrawlSourceId() {
        return crawlSourceId;
    }

    /**
     * Đặt: ID tác phẩm tại nguồn thu thập
     */
    public void setCrawlBookId(String crawlBookId) {
        this.crawlBookId = crawlBookId;
    }

    /**
     * Lấy: ID tác phẩm tại nguồn thu thập
     */
    public String getCrawlBookId() {
        return crawlBookId;
    }

    /**
     * Đặt: thời gian thu thập gần nhất
     */
    public void setCrawlLastTime(Date crawlLastTime) {
        this.crawlLastTime = crawlLastTime;
    }

    /**
     * Lấy: thời gian thu thập gần nhất
     */
    public Date getCrawlLastTime() {
        return crawlLastTime;
    }

    /**
     * Đặt: đã dừng cập nhật: 0 chưa, 1 đã dừng
     */
    public void setCrawlIsStop(Integer crawlIsStop) {
        this.crawlIsStop = crawlIsStop;
    }

    /**
     * Lấy: đã dừng cập nhật: 0 chưa, 1 đã dừng
     */
    public Integer getCrawlIsStop() {
        return crawlIsStop;
    }
}
