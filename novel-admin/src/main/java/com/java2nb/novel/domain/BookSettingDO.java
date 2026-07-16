package com.java2nb.novel.domain;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.java2nb.common.jsonserializer.LongToStringSerializer;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;


/**
 * Bảng cấu hình tác phẩm trang chủ
 *
 * @author xiongxy
 * @email 1179705413@qq.com
 * @date 2023-04-18 10:01:13
 */
public class BookSettingDO implements Serializable {

    private static final long serialVersionUID = 1L;


    //
    // Kiểu long của Java có phạm vi lớn hơn number của JavaScript, nên một số giá trị không thể biểu diễn chính xác trong JavaScript
    // Vì vậy, giá trị được tuần tự hóa thành chuỗi để xử lý
    @JsonSerialize(using = LongToStringSerializer.class)
    private Long id;
    // ID tác phẩm
    // Kiểu long của Java có phạm vi lớn hơn number của JavaScript, nên một số giá trị không thể biểu diễn chính xác trong JavaScript
    // Vì vậy, giá trị được tuần tự hóa thành chuỗi để xử lý
    @JsonSerialize(using = LongToStringSerializer.class)
    private Long bookId;
    // Số thứ tự
    private Integer sort;
    // Loại: 0 ảnh trình chiếu, 1 khu tác phẩm đầu trang, 2 đề cử tuần, 3 phổ biến, 4 chọn lọc
    private Integer type;
    // Thời gian tạo
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
    // ID người tạo
    // Kiểu long của Java có phạm vi lớn hơn number của JavaScript, nên một số giá trị không thể biểu diễn chính xác trong JavaScript
    // Vì vậy, giá trị được tuần tự hóa thành chuỗi để xử lý
    @JsonSerialize(using = LongToStringSerializer.class)
    private Long createUserId;
    // Thời gian cập nhật
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
    // ID người cập nhật
    // Kiểu long của Java có phạm vi lớn hơn number của JavaScript, nên một số giá trị không thể biểu diễn chính xác trong JavaScript
    // Vì vậy, giá trị được tuần tự hóa thành chuỗi để xử lý
    @JsonSerialize(using = LongToStringSerializer.class)
    private Long updateUserId;

    private String bookName;

    public String getBookName() {
        return bookName;
    }

    public void setBookName(String bookName) {
        this.bookName = bookName;
    }

    /**
     * Đặt:
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Lấy:
     */
    public Long getId() {
        return id;
    }

    /**
     * Đặt: ID tác phẩm
     */
    public void setBookId(Long bookId) {
        this.bookId = bookId;
    }

    /**
     * Lấy: ID tác phẩm
     */
    public Long getBookId() {
        return bookId;
    }

    /**
     * Đặt: số thứ tự
     */
    public void setSort(Integer sort) {
        this.sort = sort;
    }

    /**
     * Lấy: số thứ tự
     */
    public Integer getSort() {
        return sort;
    }

    /**
     * Đặt: loại: 0 ảnh trình chiếu, 1 khu tác phẩm đầu trang, 2 đề cử tuần, 3 đề xuất phổ biến, 4 đề xuất chọn lọc
     */
    public void setType(Integer type) {
        this.type = type;
    }

    /**
     * Lấy: loại: 0 ảnh trình chiếu, 1 khu tác phẩm đầu trang, 2 đề cử tuần, 3 đề xuất phổ biến, 4 đề xuất chọn lọc
     */
    public Integer getType() {
        return type;
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
     * Đặt: ID người tạo
     */
    public void setCreateUserId(Long createUserId) {
        this.createUserId = createUserId;
    }

    /**
     * Lấy: ID người tạo
     */
    public Long getCreateUserId() {
        return createUserId;
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
     * Đặt: ID người cập nhật
     */
    public void setUpdateUserId(Long updateUserId) {
        this.updateUserId = updateUserId;
    }

    /**
     * Lấy: ID người cập nhật
     */
    public Long getUpdateUserId() {
        return updateUserId;
    }
}
